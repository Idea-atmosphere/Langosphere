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
//   window.__zonoGenerate(index, voiceId, text, optsJson, takeKey?) -> Promise<'OK'>
//     (takeKey: when given, the generated WAV is kept in a small in-page cache
//      so __zonoReplay can play the SAME take again without re-generating)
//   window.__zonoReplay(index, takeKey) -> Promise<'OK' | 'MISS'>
//   window.__zonoPause() / __zonoResume() / __zonoSeek(seconds) / __zonoSetRate(rate)
//     transport for the take that is playing; the page reports
//     {"type":"time","index","position","duration","paused","rate"} on every timeupdate
//   window.__zonoStop() -> void
//   window.__zonoExport(index, voiceId, text, optsJson) -> Promise<'OK'> (blob download)
//   window.__zonoSetModelsBase(url) -> void
//   window.__zonoCheckModels() -> Promise<{installed: string[], missing: string[]}>
//   window.__zonoInit() -> Promise<void> (explicit init; generate/export init lazily too)
//   window.ZonoNative.onEvent(jsonString) events: ready/progress/playing/done/error/voices
//     + {"type":"takes","keys":[...]}  (the cached-take keys, after every cache change)
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
  // Detach the take's listeners first: the pause below must not report a
  // stale "paused" time event, and the shared <audio> must not keep the
  // old take's 'ended' / 'timeupdate' handlers for the next take.
  if (c.cleanup) { try { c.cleanup(); } catch {} }
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

/**
 * Generated takes, by caller key (e.g. "cue-12"). Generation on a phone takes
 * seconds per sentence, so a learner who wants to hear a line again gets the
 * same take back instantly. Bounded by count and bytes; oldest first.
 */
const TAKE_MAX_COUNT = 24;
const TAKE_MAX_BYTES = 32 * 1024 * 1024;
const takes = new Map(); // takeKey -> Blob (insertion order = age)
let takeBytes = 0;

function emitTakes() {
  emit({ type: 'takes', keys: Array.from(takes.keys()) });
}

function rememberTake(takeKey, blob) {
  if (!takeKey) return;
  const old = takes.get(takeKey);
  if (old) { takes.delete(takeKey); takeBytes -= old.size; }
  takes.set(takeKey, blob);
  takeBytes += blob.size;
  while (takes.size > TAKE_MAX_COUNT || (takeBytes > TAKE_MAX_BYTES && takes.size > 1)) {
    const oldestKey = takes.keys().next().value;
    const oldest = takes.get(oldestKey);
    takes.delete(oldestKey);
    takeBytes -= oldest ? oldest.size : 0;
  }
  emitTakes();
}

/** Playback speed of the <audio> (pitch preserved by the browser); kept across takes. */
let playbackRate = 1.0;

function timeEvent(index, audio) {
  const d = audio.duration;
  return {
    type: 'time', index,
    position: audio.currentTime || 0,
    duration: (typeof d === 'number' && isFinite(d)) ? d : 0,
    paused: !!audio.paused,
    rate: audio.playbackRate || playbackRate,
  };
}

/** Play one WAV blob through the shared <audio>; resolves 'OK' on 'ended'. */
function playBlob(index, blob, ctrl) {
  const url = URL.createObjectURL(blob);
  const audio = getAudio();
  emit({ type: 'playing', index });
  return new Promise((resolve, reject) => {
    const cur = { ctrl, audio, url, reject, index, cleanup: null };
    current = cur;
    const onTime = () => emit(timeEvent(index, audio));
    const TIME_EVENTS = ['timeupdate', 'play', 'pause', 'seeked', 'durationchange', 'ratechange', 'loadedmetadata'];
    const cleanup = () => {
      audio.removeEventListener('ended', onEnded);
      audio.removeEventListener('error', onErr);
      for (const t of TIME_EVENTS) audio.removeEventListener(t, onTime);
      if (current === cur) current = null;
      URL.revokeObjectURL(url);
    };
    cur.cleanup = cleanup;
    const onEnded = () => { onTime(); cleanup(); emit({ type: 'done', index }); resolve('OK'); };
    const onErr = () => {
      cleanup();
      const m = (audio.error && audio.error.message) || 'audio playback failed';
      emit({ type: 'error', message: m });
      reject(new Error(m));
    };
    audio.addEventListener('ended', onEnded);
    audio.addEventListener('error', onErr);
    for (const t of TIME_EVENTS) audio.addEventListener(t, onTime);
    try { audio.preservesPitch = true; } catch {}
    audio.defaultPlaybackRate = playbackRate;
    audio.src = url;
    audio.playbackRate = playbackRate;
    const p = audio.play();
    if (p && p.catch) p.catch(e => { cleanup(); reject(e); });
  });
}

// Transport for the take that is playing (no-ops when nothing plays).
window.__zonoPause = function () {
  if (current && current.audio) { try { current.audio.pause(); } catch {} }
};
window.__zonoResume = function () {
  if (current && current.audio) {
    const p = current.audio.play();
    if (p && p.catch) p.catch(() => {});
  }
};
window.__zonoSeek = function (seconds) {
  if (!current || !current.audio) return;
  const a = current.audio;
  const d = (typeof a.duration === 'number' && isFinite(a.duration)) ? a.duration : Infinity;
  const t = Math.max(0, Math.min(Number(seconds) || 0, d > 0.05 ? d - 0.05 : 0));
  try { a.currentTime = t; } catch {}
  emit(timeEvent(current.index, a));
};
window.__zonoSetRate = function (rate) {
  const r = Number(rate);
  playbackRate = isFinite(r) ? Math.max(0.5, Math.min(2.0, r)) : 1.0;
  if (current && current.audio) {
    try { current.audio.playbackRate = playbackRate; } catch {}
  }
};

window.__zonoGenerate = function (index, voiceId, text, optsJson, takeKey) {
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
    rememberTake(takeKey, blob);
    return await playBlob(index, blob, ctrl);
  })();
};

/** Play a cached take again (no generation). 'MISS' when it is gone (evicted / page reloaded). */
window.__zonoReplay = function (index, takeKey) {
  return (async () => {
    stopCurrent('replaced');
    const blob = takes.get(takeKey);
    if (!blob) { emitTakes(); return 'MISS'; }
    return await playBlob(index, blob, new AbortController());
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
