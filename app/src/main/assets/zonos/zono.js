// Langosphere TTS — minimal WebGPU player wrapper around Zyphra's zonos3-web engine.
// Engine: ../studio/web-engine/engine.js (built from Zyphra/ZONOS3-Mini servers/web,
// vite build) — the same files the bundled Voice Studio uses, so the 1 MB worker
// ships once in the APK.
//
// Models are separate from the app: the app is ONLY the runner. No model
// weights are bundled in the APK and NOTHING is auto-downloaded on first run.
// The engine starts WITHOUT models. The app (Kotlin) owns the pack downloader/importer
// and keeps packs in Download/Langosphere/models/; it serves them to this page as
// /models/* (WebViewAssetLoader / shouldInterceptRequest). This wrapper only:
//   - points the engine at a models base URL (__zonoSetModelsBase),
//   - checks which packs are installed (__zonoCheckModels -> no-model / models-ok),
//   - loads the engine lazily on first generate, from that base.
// Default base is the Hugging Face web/ folder (fallback only; never auto-fetched).
//
// JS API (driven from Kotlin, see com.example.zonos.ZonoEngine):
//   window.__zonoGenerate(index, voiceId, text, optsJson) -> Promise<'OK'>
//   window.__zonoStop() -> void
//   window.__zonoExport(index, voiceId, text, optsJson) -> Promise<'OK'> (blob download)
//   window.__zonoSetModelsBase(url) -> void
//   window.__zonoCheckModels() -> Promise<{installed: string[], missing: string[]}>
//   window.__zonoInit() -> Promise<void> (explicit init; generate/export init lazily too)
//   window.ZonoNative.onEvent(jsonString) events: ready/progress/playing/done/error/voices
//     + {"type":"no-model","missing":["backbone","zac", ...]}  (CORE or any pack absent)
//     + {"type":"models-ok","installed":["backbone","zac", ...]}
// Extra (additive): window.__zonoControls() -> Promise<object|null> (/v2/controls doc),
//   window.__zonoCapabilities() -> Promise<object|null> (/v2/capabilities doc).
import { engine, localFetch } from '../studio/web-engine/engine.js';

const HF_BASE = 'https://huggingface.co/Zyphra/ZONOS3-Mini/resolve/main/web/';
const DEFAULT_VOICE = 'default_940eaff39a929da0'; // Willow en asrm F 1

/** Pack dirs (Zyphra web format: <dir>/manifest.json + shard-*.bin). CORE = backbone+zac. */
const PACK_DIRS = ['backbone', 'zac', 'spk', 'encoder', 'teacher', 'edit', 'aligner', 'zac-full_mr'];
const CORE_DIRS = ['backbone', 'zac'];

let modelsBase = HF_BASE;
let ready = false;
let readyPromise = null;
let current = null; // { abort, audio, url, reject, index }
let audioEl = null;

function emit(obj) {
  const s = JSON.stringify(obj);
  try {
    if (window.ZonoNative && typeof window.ZonoNative.onEvent === 'function') window.ZonoNative.onEvent(s);
  } catch (e) { console.warn('ZonoNative.onEvent failed', e); }
  console.log('[zono]', s);
}

function phaseOf(models) {
  if (!models || !models.length) return 'backbone';
  if (models.includes('backbone')) return 'backbone';
  if (models.includes('zac') || models.includes('zac-full_mr')) return 'zac';
  return 'spk'; // spk / encoder / teacher / edit / aligner
}

function onProgress(p) {
  emit({ type: 'progress', loaded: p.loaded, total: p.total, phase: phaseOf(p.models) });
}

/** Local packs already sit on disk: never duplicate them into Cache Storage. */
function useCacheFor(base) {
  try { return new URL(base).origin !== location.origin; } catch { return true; }
}

window.__zonoSetModelsBase = function (url) {
  if (typeof url === 'string' && url) {
    modelsBase = url.endsWith('/') ? url : url + '/';
    readyPromise = null; // base changed: a fresh init is required
    ready = false;
  }
};

/** Probe a shard with a 1-byte ranged GET (the primary probe). v0.2.7:
 *  HEAD-first was the bug: through shouldInterceptRequest a HEAD answered
 *  with Content-Length + an empty body makes Chromium reject the fetch, so
 *  every shard reported missing even though the files are all present
 *  (v0.2.6 log: OPEN TEST 5/5, manifests fetch+parse fine). A ranged GET
 *  carries a real 1-byte body and works through the interceptor; HEAD
 *  stays only as a fallback for servers that answer it properly. */
async function shardPresent(url) {
  try {
    const r = await fetch(url, { headers: { Range: 'bytes=0-0' } });
    if (r.ok || r.status === 206) return true;
  } catch { /* fall through to HEAD */ }
  try {
    const r = await fetch(url, { method: 'HEAD' });
    if (r.ok) {
      const len = Number(r.headers.get('content-length') || '1');
      return len > 0;
    }
    return false;
  } catch { return false; }
}

/** Check one pack dir: manifest.json must parse and list shards; every shard must answer. */
async function checkPack(dir) {
  try {
    const manRes = await fetch(modelsBase + dir + '/manifest.json');
    if (!manRes.ok) return { dir, ok: false, reason: 'manifest HTTP ' + manRes.status };
    const man = await manRes.json();
    if (man.format !== 'zonos3-web-v1' || !Array.isArray(man.shards) || !man.shards.length) {
      return { dir, ok: false, reason: 'bad manifest format' };
    }
    const missing = [];
    for (const sh of man.shards) {
      if (!(await shardPresent(modelsBase + dir + '/' + sh))) missing.push(sh);
    }
    return missing.length
      ? { dir, ok: false, reason: 'missing shards: ' + missing.join(', ') }
      : { dir, ok: true };
  } catch (e) {
    return { dir, ok: false, reason: String((e && e.message) || e) };
  }
}

/**
 * Report which packs are installed at the current models base. Emits:
 *   {"type":"no-model","missing":[...]} when any pack dir is absent/incomplete, or
 *   {"type":"models-ok","installed":[...]} when every pack dir is present.
 * Generation needs CORE (backbone+zac); the other packs only gate their own features.
 */
window.__zonoCheckModels = async function () {
  const results = [];
  for (const dir of PACK_DIRS) results.push(await checkPack(dir));
  const installed = results.filter(r => r.ok).map(r => r.dir);
  const missing = results.filter(r => !r.ok).map(r => r.dir);
  if (missing.length) emit({ type: 'no-model', missing });
  else emit({ type: 'models-ok', installed });
  return { installed, missing, detail: results };
};

async function initEngine() {
  if (readyPromise) return readyPromise;
  readyPromise = (async () => {
    if (!('gpu' in navigator) || !navigator.gpu) {
      throw new Error('WebGPU is not available in this browser');
    }
    // No silent ~900 MB download: refuse to init when the CORE pack is absent.
    const { missing } = await window.__zonoCheckModels();
    const coreMissing = CORE_DIRS.filter(d => missing.includes(d));
    if (coreMissing.length) {
      throw new Error('model pack not installed: ' + coreMissing.join(', ') +
        ' — download or import the CORE pack first');
    }
    engine.onFatal(m => emit({ type: 'error', message: String(m) }));
    engine.onEncoderProgress(onProgress);
    await engine.init(onProgress, { base: modelsBase, cache: useCacheFor(modelsBase) });
    ready = true;
    emit({ type: 'ready' });
    try {
      const v = await fetch(modelsBase + 'voices/voices.json').then(r => r.json());
      emit({ type: 'voices', count: Array.isArray(v) ? v.length : 0 });
    } catch (e) {
      emit({ type: 'voices', count: 0 });
    }
  })().catch(e => {
    readyPromise = null;
    const msg = e && e.message ? e.message : String(e);
    emit({ type: 'error', message: msg });
    throw e;
  });
  return readyPromise;
}

window.__zonoInit = function () { return initEngine(); };

/** Parse optsJson (string or object) from Kotlin: {controls?: {...}, language?: string}. */
function parseOpts(optsJson) {
  if (!optsJson) return {};
  try { return typeof optsJson === 'string' ? JSON.parse(optsJson) : optsJson; }
  catch { return {}; }
}

async function requestWav(voiceId, text, opts) {
  const body = {
    text,
    language: opts.language || 'en_us',
    speaker_embedding_id: voiceId || DEFAULT_VOICE,
    format: 'wav',
    stream: false,
  };
  if (opts.controls && typeof opts.controls === 'object') body.controls = opts.controls;
  // v0.2.16: fields the Studio's buildRequest sends that the reader never
  // did — decoder (only when the project picks a non-default one),
  // LACI (only when the server has alignment heads for the language;
  // Kotlin gates on /v2/capabilities) and its whole-take decode.
  // v0.2.19: speaker_mode is passed through when the opts carry it
  // (Kotlin always sends it now). It must NEVER be omitted: the
  // server defaults to the checkpoint's FIRST mode when the field is
  // absent (generate.ts planGenerate resolve_speaker_mode: `let mode =
  // modes[0]`; speakers.ts checkpointModes() returns
  // ['reference', 'embedding']), and in 'reference' mode any
  // embedding-moving control 400s ("need speaker_mode=embedding",
  // log #12). The old comment here claimed omitting it was safe
  // because library voices are "embedding only" — true about the
  // voice, wrong about the server default. The Studio always sends
  // speaker_mode: d.mode (requests.ts:132), so the app does too.
  if (opts.decoder) body.decoder = opts.decoder;
  if (opts.laci === true) body.laci = true;
  if (opts.stream_decode) body.stream_decode = opts.stream_decode;
  if (opts.speaker_mode) body.speaker_mode = opts.speaker_mode;
  const ctrl = new AbortController();
  const req = localFetch('/v2/generate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
    signal: ctrl.signal,
  });
  return { req, ctrl };
}

function stopCurrent(reason) {
  if (!current) return;
  const c = current;
  current = null;
  try { c.ctrl.abort(); } catch {}
  if (c.audio) { try { c.audio.pause(); } catch {} }
  if (c.url) { try { URL.revokeObjectURL(c.url); } catch {} }
  if (c.reject) c.reject(new Error(reason || 'stopped'));
}

function getAudio() {
  if (!audioEl) {
    audioEl = document.createElement('audio');
    audioEl.setAttribute('playsinline', '');
    audioEl.preload = 'auto';
    document.body.appendChild(audioEl);
  }
  return audioEl;
}

window.__zonoGenerate = function (index, voiceId, text, optsJson) {
  return (async () => {
    stopCurrent('replaced');
    await initEngine(); // lazy: first call loads the engine from the installed packs
    const opts = parseOpts(optsJson);
    const { req, ctrl } = await requestWav(voiceId, text, opts);
    const res = await req;
    if (!res.ok) {
      const t = await res.text().catch(() => '');
      throw new Error('generate failed: HTTP ' + res.status + (t ? ' ' + t.slice(0, 300) : ''));
    }
    const buf = await res.arrayBuffer();
    const blob = new Blob([buf], { type: 'audio/wav' });
    const url = URL.createObjectURL(blob);
    const audio = getAudio();
    emit({ type: 'playing', index });
    return await new Promise((resolve, reject) => {
      const cur = { ctrl, audio, url, reject, index };
      current = cur;
      const cleanup = () => {
        audio.removeEventListener('ended', onEnded);
        audio.removeEventListener('error', onErr);
        if (current === cur) current = null;
        URL.revokeObjectURL(url);
      };
      const onEnded = () => { cleanup(); emit({ type: 'done', index }); resolve('OK'); };
      const onErr = () => {
        cleanup();
        const m = (audio.error && audio.error.message) || 'audio playback failed';
        emit({ type: 'error', message: m });
        reject(new Error(m));
      };
      audio.addEventListener('ended', onEnded);
      audio.addEventListener('error', onErr);
      audio.src = url;
      const p = audio.play();
      if (p && p.catch) p.catch(e => { cleanup(); reject(e); });
    });
  })();
};

window.__zonoStop = function () {
  stopCurrent('stopped');
};

window.__zonoExport = function (index, voiceId, text, optsJson) {
  return (async () => {
    await initEngine(); // lazy: first call loads the engine from the installed packs
    const opts = parseOpts(optsJson);
    const { req, ctrl } = await requestWav(voiceId, text, opts);
    const res = await req;
    if (!res.ok) {
      const t = await res.text().catch(() => '');
      throw new Error('generate failed: HTTP ' + res.status + (t ? ' ' + t.slice(0, 300) : ''));
    }
    const buf = await res.arrayBuffer();
    const blob = new Blob([buf], { type: 'audio/wav' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'zono-' + index + '.wav';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60 * 1000);
    emit({ type: 'done', index });
    return 'OK';
  })();
};

// Additive helper (not in CONTRACT.md): steering controls document, e.g. pace.
window.__zonoControls = async function () {
  await initEngine();
  const res = await localFetch('/v2/controls');
  if (!res.ok) return null;
  return await res.json();
};

// v0.2.16: engine capabilities document (decoders served,
// laci_languages) — the reader gates its LACI check on it.
window.__zonoCapabilities = async function () {
  await initEngine();
  const res = await localFetch('/v2/capabilities');
  if (!res.ok) return null;
  return await res.json();
};

// NOTE (Addendum 2): NO auto-init here on purpose. The page loads with no models and
// no network traffic; Kotlin drives __zonoSetModelsBase -> __zonoCheckModels -> generate.
