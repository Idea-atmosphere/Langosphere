package com.example.zonos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The full Zyphra Voice Studio page (Cast / Direct / Edit), shown inside
 * the Learning tools ▸ TTS section.
 *
 * Loads the PREBUILT studio dist verbatim from assets/studio/ (its
 * root-absolute paths were already rewritten to relative ones). The
 * loader maps "/assets/" to the assets root (same as the hidden player):
 *   /assets/studio/studio.html        -> assets/studio/studio.html
 *   /assets/studio/assets/studio-*.js -> assets/studio/assets/studio-*.js
 *   /models/...                       -> Download/Langosphere/models/... (ModelFileServer)
 *
 * EXACT URL:
 *   CORE installed:
 *     .../assets/studio/studio.html?api=web
 *       &engine=/assets/studio/web-engine/engine.js
 *       &models=https://appassets.androidplatform.net/models/
 *     (?api=web forces the in-browser backend; ?engine= must be
 *     origin-absolute because dynamic import() resolves relative URLs
 *     against the JS bundle, not the page; ?models= is the local packs
 *     base served by ModelFileServer.)
 *   CORE NOT installed:
 *     .../assets/studio/studio.html   (no query — `auto` default)
 *     NEVER open with ?api=web pre-install: the Studio would auto-init
 *     the engine and start the ~900 MB download by itself.
 *
 * Caveats:
 * - The Studio's engine init always uses cache:true, so with a local
 *   ?models= the CORE pack is ALSO copied into Cache Storage
 *   'zonos3-web-models-v6' — a duplicate of Download/Langosphere/models/.
 *   Disk cost only; deleting the pack clears that copy via clearModelCache().
 * - Service-worker registration (/sw.js) 404s under the asset loader; the
 *   Studio catches and ignores it.
 * - The app has no RECORD_AUDIO permission: in-Studio microphone recording
 *   is denied by the WebView; file upload works through the SAF chooser.
 *
 * The voice designs made here (bench + personas of the open project) are
 * read back by ZonoEngine.fetchStudioDoc() and applied to the subtitle /
 * book readers through StudioDesign.resolveReaderDesign().
 */
class StudioEngine(private val context: Context) {

    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val STUDIO_BASE_URL = "$ORIGIN/assets/studio/studio.html"
        const val MODELS_BASE_URL = "$ORIGIN/models/"
        const val ENGINE_URL_PARAM = "/assets/studio/web-engine/engine.js"

        fun studioUrl(coreInstalled: Boolean): String =
            if (coreInstalled) {
                "$STUDIO_BASE_URL?api=web&engine=$ENGINE_URL_PARAM&models=$MODELS_BASE_URL"
            } else {
                STUDIO_BASE_URL
            }

        /**
         * Pixel height fix: percentage / dvh heights never resolve in
         * this WebView's layout (html/body/#app all compute to 0px), so
         * the document, app shell, stage and every modal get explicit
         * pixel heights from window.innerHeight, re-applied by a
         * MutationObserver and on resize. Shipped Studio CSS/JS untouched.
         */
        const val STUDIO_PIXEL_HEIGHT_FIX_JS = """
(function(){try{
var h=window.innerHeight||0;
if(!h)return;
window.__zonoPixelHeights=true;
var px=h+'px';
var set=function(el){if(el){el.style.height=px}};
set(document.documentElement);
set(document.body);
set(document.getElementById('app'));
set(document.querySelector('.app'));
var stage=document.getElementById('stage');
if(stage){
var r=stage.getBoundingClientRect();
if(r.height===0){
var sh=Math.max(0,h-Math.round(r.top))+'px';
stage.style.height=sh;
stage.style.minHeight=sh;
}
}
var sizeModals=function(){
try{
var ih=window.innerHeight||0;
var iw=window.innerWidth||0;
if(!ih||!iw)return;
var i,els;
els=document.querySelectorAll('.card-modal');
for(i=0;i<els.length;i++){els[i].style.height=ih+'px';els[i].style.width=iw+'px';}
els=document.querySelectorAll('.mood-pad');
for(i=0;i<els.length;i++){els[i].style.maxWidth=Math.floor(ih*0.48)+'px';}
els=document.querySelectorAll('.atlas-map');
for(i=0;i<els.length;i++){els[i].style.maxHeight=Math.floor(ih*0.64)+'px';}
var mh=Math.min(iw-80,Math.floor(ih*0.52));
if(mh<0)mh=0;
els=document.querySelectorAll('.who.is-large');
for(i=0;i<els.length;i++){els[i].style.setProperty('--map-h',mh+'px');}
els=document.querySelectorAll('.proj-launcher');
for(i=0;i<els.length;i++){els[i].style.maxHeight=Math.floor(ih*0.84)+'px';}
els=document.querySelectorAll('.sheet');
for(i=0;i<els.length;i++){els[i].style.maxHeight=Math.floor(ih*0.88)+'px';}
els=document.querySelectorAll('.brushbar');
for(i=0;i<els.length;i++){els[i].style.maxHeight=Math.floor(ih*0.62)+'px';}
els=document.querySelectorAll('.proj-backdrop');
for(i=0;i<els.length;i++){els[i].style.paddingTop=Math.floor(ih*0.08)+'px';}
}catch(e){}
};
sizeModals();
if(!window.__zonoModalObserver){
try{
var obs=new MutationObserver(function(){sizeModals();});
var tgt=document.body||document.documentElement;
if(tgt){obs.observe(tgt,{childList:true,subtree:true});window.__zonoModalObserver=obs;}
}catch(e){}
}
if(!window.__zonoModalResize){
window.__zonoModalResize=true;
try{window.addEventListener('resize',sizeModals);}catch(e){}
}
}catch(e){}})()
"""

        /**
         * Export hook: the Studio exports audio via blob: anchor clicks,
         * which a DownloadListener cannot fetch — intercept the click,
         * read the blob, post it back as base64 through the bridge; the
         * Kotlin side saves it under Download/Langosphere/models/exports/.
         */
        const val STUDIO_EXPORT_HOOK_JS = """
(function(){try{
if(window.__zonoStudioExportHook)return;
window.__zonoStudioExportHook=true;
var origClick=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){
var href=this.href;var name=this.download||'studio-export.wav';
if(href&&href.indexOf('blob:')===0){
fetch(href).then(function(r){return r.blob()}).then(function(b){
var fr=new FileReader();
fr.onload=function(){var du=fr.result;var b64=du.split(',')[1];
window.ZonoNative.onEvent(JSON.stringify({type:'exportData',name:name,base64:b64}))};
fr.onerror=function(){try{window.ZonoNative.onEvent(JSON.stringify({type:'exportError',name:name,message:'could not read blob: '+(fr.error?String(fr.error):'FileReader error')}))}catch(_){}};
fr.readAsDataURL(b);}).catch(function(e){try{window.ZonoNative.onEvent(JSON.stringify({type:'exportError',name:name,message:String(e&&e.message?e.message:e)}))}catch(_){}});}
return origClick.call(this);};}catch(e){}})()
"""

        /**
         * Persian Studio UI with a Persian/English toggle — runtime
         * dictionary translation via injected JS. The Studio has no i18n
         * system, so the dist is NOT edited: this layer walks text nodes
         * + title/placeholder/aria-label attributes and replaces exact
         * matches, re-applied by a MutationObserver. User content
         * (script lines, transcripts, voice names, inputs) is never
         * translated. The toggle («EN» / «فا» in the top bar) flips live
         * and persists through the bridge ({type:'studioLang'}).
         */
        const val STUDIO_FA_TRANSLATE_JS = """
(function(){try{
if(window.__zonoFaInstalled)return;window.__zonoFaInstalled=true;
var DICT={
'Voice Studio':'استودیو صدا',
'Cast':'طراحی صدا',
'Direct':'متن',
'Edit':'ویرایش',
'All changes saved':'همهٔ تغییرات ذخیره شد',
'Opening…':'در حال باز شدن…',
'Loading the in-browser engine…':'در حال بارگذاری موتور در مرورگر…',
'Untitled project':'پروژهٔ بی‌نام',
'Who':'هویت',
'Accent':'لهجه',
'Texture':'بافت',
'Room':'فضا',
'Delivery':'اجرا',
'Mood':'حال‌وهوا',
'Gender':'جنسیت',
'Age':'سن',
'Pitch':'زیروبمی',
'Intonation':'آهنگ',
'Deep':'عمیق',
'Bright':'شفاف',
'Husky':'خش‌دار',
'Raspy':'گرفته',
'Soft':'نرم',
'Authority':'مقتدر',
'Projection':'بلندی صدا',
'Pace':'سرعت',
'Read ↔ chatty':'رسمی ↔ محاوره‌ای',
'Hesitation':'تردید',
'Liveliness':'سرزندگی',
'Emphasis':'تأکید',
'Valence':'مثبت‌بودن',
'Arousal':'برانگیختگی',
'Angry':'عصبانی',
'Happy':'شاد',
'Sad':'غمگین',
'Calm':'آرام',
'Scared':'ترسیده',
'Confused':'گیج',
'North America':'آمریکای شمالی',
'England':'انگلستان',
'Scotland':'اسکاتلند',
'Ireland':'ایرلند',
'Wales':'ولز',
'Australia':'استرالیا',
'India':'هند',
'Hindi':'هندی',
'Spanish':'اسپانیایی',
'French':'فرانسوی',
'Portuguese':'پرتغالی',
'German':'آلمانی',
'Vietnamese':'ویتنامی',
'Polish':'لهستانی',
'Mandarin':'ماندارین',
'BBC newsreader':'گویندهٔ خبری بی‌بی‌سی',
'Elderly Scottish narrator':'راوی پیر اسکاتلندی',
'Young Australian':'استرالیایی جوان',
'Gravelly old man':'پیرمرد خش‌صدا',
'Soft-spoken Irish woman':'زن ایرلندی آرام',
'Indian English caller':'تماس‌گیرندهٔ هندی',
'Chatty podcaster':'پادکستر پرحرف',
'Late-night radio':'رادیوی نیمه‌شب',
'Voices':'صداها',
'Your cast':'گروه صداها',
'Your voices':'صداهای شما',
'Save this voice':'ذخیرهٔ این صدا',
'Add to cast':'افزودن به گروه',
'(use in Direct)':'(برای استفاده در متن)',
'Save to Your voices':'ذخیره در صداهای شما',
'Saving…':'در حال ذخیره…',
'Starting points':'نقطه‌های شروع',
'Roll a voice':'صدای تصادفی',
'Shape the voice':'شکل‌دهی صدا',
'Shape it':'شکلش بده',
'Switch to Shape it':'برو به شکل‌دهی',
'Designable':'قابل طراحی',
'Exact clone':'کپی دقیق',
'Keep as is':'همین‌طور بماند',
'Keep as is copies the recording.':'«همین‌طور بماند» ضبط را کپی می‌کند.',
'Keep as is needs a recording; this voice was saved without one.':'«همین‌طور بماند» به ضبط نیاز دارد؛ این صدا بدون ضبط ذخیره شده.',
'Same person (the recording)':'همان شخص (ضبط)',
'Pick a voice in Cast first.':'اول در «طراحی صدا» یک صدا انتخاب کن.',
'Pick a voice · type a line · Speak · then shape it below':'یک صدا انتخاب کن · یک خط بنویس · بگو · بعد پایین شکلش بده',
'Voices near this one':'صداهای نزدیک به این',
'Five small, safe moves around this voice, rendered side by side. Click one to step there.':'پنج حرکت کوچک و امن دور این صدا، کنار هم رندر شده. روی یکی کلیک کن تا به آن برسی.',
'Upload, drop or record a clip to clone a voice. Designed voices you save appear here too.':'برای کلون صدا، کلیپ آپلود کن، بکش‌ورها کن یا ضبط کن. صداهای طراحی‌شده‌ای که ذخیره می‌کنی هم اینجا می‌آیند.',
'Short: 5–30 s clones best':'کوتاه: ۵ تا ۳۰ ثانیه بهترین کلون را می‌دهد',
'Record':'ضبط',
'Use':'استفاده',
'No accent':'بدون لهجه',
'Neutral':'خنثی',
'Moods':'حال‌وهواها',
'New person':'شخص جدید',
'age (years)':'سن (سال)',
'drag into a region · deeper = stronger':'به داخل یک ناحیه بکش · عمیق‌تر = قوی‌تر',
'drag the dot · scroll for pitch':'نقطه را بکش · برای زیروبمی اسکرول کن',
'home · no accent':'خانه · بدون لهجه',
'no delivery moves':'بدون حرکت اجرا',
'pin at home':'سنجاق در خانه',
'No room control':'بدون کنترل اتاق',
'This voice bundle has no gender / age controls.':'این بستهٔ صدا کنترل جنسیت / سن ندارد.',
'Delivery and mood still work; Who, Accent, Texture and Room don’t. Switch to Shape it to change them; your moves here are kept.':'اجرا و حال‌وهوا هنوز کار می‌کنند؛ هویت، لهجه، بافت و فضا نه. برای عوض کردنشان به «شکل‌دهی» برو؛ حرکت‌هایت اینجا حفظ می‌شوند.',
'Landmarks sit where each mood measured at its default dose. Click one to add it, click again to remove; drag it or its handle along the line for strength. Arrow keys move between moods, + / − change the dose.':'نشانه‌ها جایی هستند که هر حال‌وهوا در دوز پیش‌فرضش اندازه‌گیری شده. روی یکی کلیک کن تا اضافه شود، دوباره کلیک کن تا حذف شود؛ آن یا دستگیره‌اش را روی خط بکش تا قدرت عوض شود. کلیدهای جهت‌دار بین حال‌وهواها جابه‌جا می‌شوند، + / − دوز را عوض می‌کنند.',
'You moved since these were found; they still sit around the earlier voice.':'از وقتی این‌ها پیدا شدند جابه‌جا شدی؛ هنوز دور صدای قبلی هستند.',
'This server can’t apply controls yet: A and B both play the base voice.':'این سرور هنوز نمی‌تواند کنترل‌ها را اعمال کند: A و B هر دو صدای پایه را پخش می‌کنند.',
'Design a voice, then “Save to cast” to keep it for Direct (each line can use its own voice).':'صدایی طراحی کن، بعد «ذخیره در گروه» را بزن تا برای «متن» بماند (هر خط می‌تواند صدای خودش را داشته باشد).',
'Loading voices…':'در حال بارگذاری صداها…',
'Couldn’t load the voices':'صداها بارگذاری نشدند',
'Couldn’t render':'رندر نشد',
'Couldn’t render the original voice':'صدای اصلی رندر نشد',
'Counts against your GPU quota':'از سهمیهٔ GPU کم می‌کند',
'Play':'پخش',
'Fix the pronunciation marked above':'تلفظ علامت‌گذاری‌شدهٔ بالا را اصلاح کن',
'Script':'متن',
'Timeline':'خط زمانی',
'Timing':'زمان‌بندی',
'Brushes':'قلم‌ها',
'Clear paint':'پاک کردن رنگ',
'Add a line':'افزودن خط',
'Add line':'افزودن خط',
'Paste a script':'چسباندن متن',
'Paste a script, or add lines one by one; each line is rendered and directed on its own, in its own voice.':'متن را بچسبان یا خط‌به‌خط اضافه کن؛ هر خط جداگانه رندر و کارگردانی می‌شود، با صدای خودش.',
'No lines yet':'هنوز خطی نیست',
'Regenerate':'بازتولید',
'Export':'خروجی',
'Export WAV':'خروجی WAV',
'Pronunciation':'تلفظ',
'Default voice':'صدای پیش‌فرض',
'(voice unavailable)':'(صدا در دسترس نیست)',
'Not rendered':'رندر نشده',
'Apply script':'اعمال متن',
'Baseline performance':'اجرای پایه',
'Silence between lines when the script plays or exports.':'سکوت بین خط‌ها هنگام پخش یا خروجی متن.',
'Add this voice to the cast':'افزودن این صدا به گروه',
'Select words in a line to paint delivery onto them (on a phone, tap a word, then tap more to stretch the selection; or use the brush in the line’s tools for the whole line). Click painted words to change or clear them. Underlines show what is painted:':'روی کلمه‌های یک خط انتخاب کن تا اجرا روی آن‌ها رنگ شود (روی گوشی: روی یک کلمه بزن، بعد کلمه‌های بیشتری بزن تا انتخاب کش بیاید؛ یا برای کل خط از قلم ابزارهای خط استفاده کن). روی کلمه‌های رنگ‌شده کلیک کن تا عوض یا پاک شوند. خط زیر نشان می‌دهد چه چیزی رنگ شده:',
'Tap more words to stretch the selection; tap a word inside it to start over from there.':'کلمه‌های بیشتری بزن تا انتخاب کش بیاید؛ روی کلمه‌ای داخل انتخاب بزن تا از همان‌جا از نو شروع شود.',
'Edit the words':'ویرایش کلمات',
'Change words':'تغییر کلمات',
'Change the words of a recording; only the changed stretch is regenerated.':'کلمات یک ضبط را عوض کن؛ فقط بخش عوض‌شده دوباره تولید می‌شود.',
'Open a clip to edit':'کلیپی برای ویرایش باز کن',
'Open audio':'باز کردن صدا',
'Upload':'آپلود',
'Choose a take or voice':'یک برداشت یا صدا انتخاب کن',
'Takes':'برداشت‌ها',
'Takes aren’t saved until you keep one.':'برداشت‌ها تا وقتی یکی را نگه نداری ذخیره نمی‌شوند.',
'Keep':'نگه دار',
'Discard':'دور بریز',
'Done':'تمام',
'Details':'جزئیات',
'Regions':'ناحیه‌ها',
'Comparing':'مقایسه',
'Advanced':'پیشرفته',
'Seed':'بذر',
'Reuse seed':'استفادهٔ دوباره از بذر',
'Reset timings':'بازنشانی زمان‌بندی',
'Fix the transcript':'اصلاح رونوشت',
'Use this transcript':'استفاده از این رونوشت',
'Send back to Direct':'برگرداندن به متن',
'Transcribed automatically':'خودکار رونویسی شد',
'Type exactly what the clip says':'دقیقاً همان چیزی را بنویس که کلیپ می‌گوید',
'Audio the model listens to':'صدایی که مدل به آن گوش می‌دهد',
'Blend around the change':'ترکیب دور تغییر',
'All changes sound:':'همهٔ تغییرات به این شکل شنیده می‌شوند:',
'Looks right':'درست به نظر می‌رسد',
'Fix it':'اصلاحش کن',
'Try again':'دوباره تلاش کن',
'This server can’t edit speech':'این سرور نمی‌تواند گفتار را ویرایش کند',
'Changing words in a recording needs a server with the speech-editing model. Cast and Direct work as usual.':'تغییر کلمات ضبط به سروری با مدل ویرایش گفتار نیاز دارد. «طراحی صدا» و «متن» مثل همیشه کار می‌کنند.',
'clip language':'زبان کلیپ',
'pre-roll':'پیش‌پخش',
'fit':'جا دادن',
'best match':'بهترین تطابق',
'revert text':'برگرداندن متن',
'revert these words':'برگرداندن این کلمات',
'drag across the waveform':'روی موج بکش',
'— check it: play the clip and read along.':'— بررسی‌اش کن: کلیپ را پخش کن و هم‌زمان بخوان.',
'to re-say a stretch as it is. Then pick how it should sound: whisper, slower, angry…':'یک بخش را همان‌طور که هست دوباره بگو. بعد انتخاب کن چطور باید شنیده شود: زمزمه، کندتر، عصبانی…',
'No server answered':'سروری جواب نداد',
'Run in this browser':'اجرا در همین مرورگر',
'Run the voice engine in this browser? Download ≈ 1.9 GB once; cached in this browser.':'موتور صدا در همین مرورگر اجرا شود؟ یک بار حدود ۱٫۹ گیگابایت دانلود می‌شود؛ در همین مرورگر کش می‌شود.',
'Work offline':'آفلاین کار کن',
'Try it with demo audio':'با صدای نمونه امتحانش کن',
'Connect':'اتصال',
'Retry':'تلاش دوباره',
'Retry the server':'تلاش دوباره به سرور',
'Server URL':'آدرس سرور',
'Save':'ذخیره',
'Cancel':'لغو',
'Delete':'حذف',
'Duplicate':'کپی',
'Rename…':'تغییر نام…',
'Export…':'خروجی…',
'Import…':'وارد کردن…',
'Reload':'بارگذاری دوباره',
'Reload (drop my changes)':'بارگذاری دوباره (تغییراتم را دور بریز)',
'Keep mine as a copy':'مال من را به‌عنوان کپی نگه دار',
'Dismiss for this session':'برای این جلسه رد کن',
'All projects…':'همهٔ پروژه‌ها…',
'Project settings':'تنظیمات پروژه',
'Project settings…':'تنظیمات پروژه…',
'Projects':'پروژه‌ها',
'New project':'پروژهٔ جدید',
'Recent':'اخیر',
'Language':'زبان',
'Decoder':'دیکدر',
'Voice mode':'حالت صدا',
'Keyboard shortcuts':'میانبرهای صفحه‌کلید',
'Keyboard':'صفحه‌کلید',
'For whoever runs the server':'برای کسی که سرور را اجرا می‌کند',
'Direct lines and the Cast preview render in it: numbers and abbreviations are read in this language, and painted words follow the reading where the server can. A library voice of another language (German, French…) speaks its own instead; English voices keep a UK English project. New Edit clips and pronunciation lookups start in it.':'خط‌های «متن» و پیش‌نمایش «طراحی صدا» به این زبان رندر می‌شوند: عددها و مخفف‌ها به این زبان خوانده می‌شوند و کلمه‌های رنگ‌شده تا جایی که سرور بتواند از همان خوانش پیروی می‌کنند. صدای کتابخانه‌ای زبان دیگر (آلمانی، فرانسوی…) به زبان خودش حرف می‌زند؛ صداهای انگلیسی پروژهٔ انگلیسی بریتانیایی را نگه می‌دارند. کلیپ‌های جدید «ویرایش» و جست‌وجوی تلفظ هم با همین زبان شروع می‌شوند.',
'What turns the model’s codes into sound, for every render (Cast previews, Direct lines, Edit takes). Fast (keep2) runs a pruned decoder; Full-size runs all of its layers (heavier). Direct takes rendered with another decoder read as stale.':'چیزی که کدهای مدل را به صدا تبدیل می‌کند، برای هر رندر (پیش‌نمایش‌های «طراحی صدا»، خط‌های «متن»، برداشت‌های «ویرایش»). سریع (keep2) دیکدر هرس‌شده را اجرا می‌کند؛ اندازهٔ کامل همهٔ لایه‌هایش را اجرا می‌کند (سنگین‌تر). برداشت‌های «متن» که با دیکدر دیگری رندر شده‌اند کهنه خوانده می‌شوند.',
'How the Cast bench starts when a recorded voice is picked: designable (its embedding, every control works) or an exact clone of the clip (delivery and mood only).':'میز «طراحی صدا» وقتی صدای ضبط‌شده‌ای انتخاب می‌شود چطور شروع می‌شود: قابل طراحی (امبدینگ آن، همهٔ کنترل‌ها کار می‌کنند) یا کپی دقیق کلیپ (فقط اجرا و حال‌وهوا).',
'A new version is ready':'نسخهٔ جدیدی آماده است',
'Controls reset':'کنترل‌ها بازنشانی شدند',
'Switched to Designable':'به «قابل طراحی» تغییر کرد',
'Pick a voice first':'اول یک صدا انتخاب کن',
'Nothing rendered yet.':'هنوز چیزی رندر نشده.',
'Generate takes first':'اول برداشت تولید کن',
'Generate takes first, then compare.':'اول برداشت تولید کن، بعد مقایسه کن.',
'Cancelled':'لغو شد',
'Text reverted to the transcript':'متن به رونوشت برگشت',
'Word timings reset to the alignment':'زمان‌بندی کلمات به تراز برگشت',
'Recording needs HTTPS (or localhost)':'ضبط به HTTPS (یا localhost) نیاز دارد',
'That clip is too short to edit.':'این کلیپ برای ویرایش خیلی کوتاه است.',
'That file isn’t audio the browser can read (try WAV or MP3)':'این فایل صوتی نیست که مرورگر بتواند بخواند (WAV یا MP3 را امتحان کن)',
'The server restarted and forgot this voice':'سرور دوباره راه‌اندازی شد و این صدا را فراموش کرد',
'Select the words to pronounce first.':'اول کلمه‌هایی را که باید تلفظ شوند انتخاب کن.',
'Region edges follow the text again.':'لبه‌های ناحیه دوباره دنبال متن می‌روند.',
'Render some lines first: each line’s keeper plays in order.':'اول چند خط رندر کن: برداشت نگه‌داشته‌شدهٔ هر خط به‌ترتیب پخش می‌شود.',
'Keeper: the script plays this take':'نگه‌داشته‌شده: متن این برداشت را پخش می‌کند',
'IPA sent':'IPA فرستاده شد',
'This project changed elsewhere: reload it or keep yours as a copy before switching.':'این پروژه جای دیگری عوض شده: قبل از جابه‌جا شدن آن را دوباره بارگذاری کن یا مال خودت را به‌عنوان کپی نگه دار.',
'Search projects':'جست‌وجوی پروژه‌ها',
'Signature line: what this voice says when loaded (optional)':'جملهٔ امضا: این صدا هنگام بارگذاری چه می‌گوید (اختیاری)',
'What do they say?':'چه می‌گویند؟',
'Undo (⌘Z / Ctrl+Z)':'بازگردانی (⌘Z / Ctrl+Z)',
'Redo (⇧⌘Z / Ctrl+Y)':'تکرار (⇧⌘Z / Ctrl+Y)',
'Current base voice':'صدای پایهٔ فعلی',
'Use as the base voice':'به‌عنوان صدای پایه استفاده کن',

// ── v0.2.20 Task B: completed Studio Persian translation ──
// 356 entries added (total 593); every key
// verified verbatim in the shipped Studio dist bundle (see
// build/tests/voicedoses dictionary-coverage test).
'Speak':'بخوان',
'Speak again':'دوباره بخوان',
'Stop':'توقف',
'Press Speak to hear it':'«بخوان» را بزن تا بشنوی',
'Text to speak':'متن برای خواندن',
'The voice on the bench':'صدای روی میز کار',
'Waiting for the base voice…':'در انتظار صدای پایه…',
'Change the voice':'تغییر صدا',
'Clip length':'طول کلیپ',
'Designed voice waveform':'موج صدای طراحی‌شده',
'Original voice waveform':'موج صدای اصلی',
'Exact clones are already in Your voices.':'کلون‌های دقیق از قبل در «صداهای تو» هستند.',
'Find five':'پنج تا پیدا کن',
'Five more':'پنج تای دیگر',
'Renders five takes':'پنج برداشت رندر می‌کند',
'Variations':'تنوع‌ها',
'Signature line':'جملهٔ امضا',
'Name':'نام',
'Preview settings':'تنظیمات پیش‌نمایش',
'Preview':'پیش‌نمایش',
'Not available with this server’s placeholder voices.':'با صداهای جایگزین این سرور در دسترس نیست.',
'This server can’t apply controls yet, so there is no shaped voice to save.':'این سرور هنوز نمی‌تواند کنترل‌ها را اعمال کند، پس صدای شکل‌داده‌شده‌ای برای ذخیره نیست.',
'Rendering…':'در حال رندر…',
'Shape it: who, accent and texture change the person':'شکلش بده: هویت، لهجه و بافت، شخص را تغییر می‌دهند',
'Reset the controls':'بازنشانی کنترل‌ها',
'Reset: back to the plain base voice':'بازنشانی: بازگشت به صدای پایهٔ ساده',
'Keep as is sends the recording itself: the identity is the recording\'s':'ارسال «همان‌طور که هست» خودِ ضبط را می‌فرستد: هویت، خودِ ضبط است',
'This voice was saved without a recording, so there is nothing to keep as is':'این صدا بدون ضبط ذخیره شده، پس چیزی برای «همان‌طور که هست» نگه‌داشتن نیست',
'Voice bench':'میز صدا',
'Same person':'همان شخص',
'Your design (A)':'طرح تو (A)',
'The unsteered voice (B)':'صدای بدون هدایت (B)',
'Auto: renders on every change (click to render only on Speak)':'خودکار: با هر تغییر رندر می‌کند (برای رندر فقط با «بخوان» کلیک کن)',
'Renders only on Speak (click to render on every change)':'فقط با «بخوان» رندر می‌کند (برای رندر با هر تغییر کلیک کن)',
'Play: new takes play by themselves (click to keep them quiet)':'پخش: اجراهای جدید خودشان پخش می‌شوند (برای ساکت نگه‌داشتنشان کلیک کن)',
'New takes wait for Speak (click to play them by themselves)':'اجراهای جدید منتظر «بخوان» می‌مانند (برای پخش خودکارشان کلیک کن)',
'The voice changed since this take: render it again (Enter in the text box)':'صدا از زمان این برداشت تغییر کرده: دوباره رندرش کن (Enter در جعبهٔ متن)',
'Speak this text (Enter in the text box)':'خواندن این متن (Enter در جعبهٔ متن)',
'Step to this voice (undo with ⌘Z)':'رفتن به این صدا (واگرد با ⌘Z)',
'Keeps the whole design (voice, accent, delivery, mood) in this project\'s cast, for Direct\'s lines':'کل طراحی (صدا، لهجه، اجرا، حالت) را در گروه این پروژه نگه می‌دارد، برای خط‌های «متن»',
'Keeps the identity you shaped as a new voice in Your voices (delivery and mood stay in the cast member)':'هویتی که شکل دادی را به‌عنوان صدای جدید در «صداهای تو» نگه می‌دارد (اجرا و حالت در عضو گروه می‌مانند)',
'Auto':'خودکار',
'Monitor':'مانیتور',
'tense':'پرتنش',
'excited':'هیجان‌زده',
'low':'بی‌حال',
'content':'راضی',
'identity':'هویت',
'Strength':'قدرت',
'· strong':'· قوی',
'· the doses add up':'· دوزها جمع می‌شوند',
'Dot = overall tone · chips add named moods':'نقطه = لحن کلی · چیپ‌ها حالت‌های نام‌دار اضافه می‌کنند',
'Who map':'نقشهٔ Who',
'Who map: masculine to feminine (x), age in years (y). Arrow keys move; scroll while focused (or with ⌘ / Ctrl) changes pitch.':'نقشهٔ Who: مردانه تا زنانه (x)، سن به سال (y). کلیدهای جهت‌دار جابه‌جا می‌کنند؛ اسکرول وقتی تمرکز دارد (یا با ⌘ / Ctrl) زیر و بمی را تغییر می‌دهد.',
'Drag the dot to move gender / age; scroll while the map has focus (or with ⌘ / Ctrl) for pitch; click a voice to start from it':'نقطه را بکش تا جنسیت / سن جابه‌جا شود؛ برای زیر و بمی، وقتی نقشه تمرکز دارد اسکرول کن (یا با ⌘ / Ctrl)؛ روی یک صدا کلیک کن تا از آن شروع کنی',
'A random new person: base voice, who, a touch of texture or room (delivery and mood stay as they are)':'یک شخص جدید تصادفی: صدای پایه، هویت، کمی بافت یا اتاق (اجرا و حالت همان‌طور که هستند می‌مانند)',
'Keep as is: the recording itself; only delivery and mood change':'همان‌طور که هست: خودِ ضبط؛ فقط اجرا و حالت تغییر می‌کنند',
'Reset delivery and mood (Who, Accent, Texture and Room are kept for Shape it)':'بازنشانی اجرا و حالت (هویت، لهجه، بافت و اتاق برای «شکلش بده» حفظ می‌شوند)',
'· change':'· تغییر',
'designed':'طراحی‌شده',
'No reference clip (designed voice)':'کلیپ مرجع نیست (صدای طراحی‌شده)',
'Removes it from this project (the server keeps it for this session)':'آن را از این پروژه حذف می‌کند (سرور آن را برای این نشست نگه می‌دارد)',
'Rename (in this project)':'تغییر نام (در این پروژه)',
'Voice library':'کتابخانهٔ صدا',
'Reference clip':'کلیپ مرجع',
'Recording waveform':'موج ضبط',
'Your recording':'ضبط تو',
'Play the recording':'پخش ضبط',
'Drop to clone this voice':'رها کن تا این صدا کلون شود',
'An audio clip of 5–30 s of clean speech':'یک کلیپ صوتی ۵ تا ۳۰ ثانیه‌ای از گفتار تمیز',
'Record 5-30 s of clean speech to clone a voice':'۵ تا ۳۰ ثانیه گفتار تمیز ضبط کن تا صدا را کلون کنی',
'Remove':'برداشتن',
'Remove from cast':'برداشتن از گروه',
'Uploading…':'در حال بارگذاری…',
'Wait for the current upload…':'منتظر بارگذاری فعلی بمان…',
'Use its line':'استفاده از خط آن',
'no accent':'بدون لهجه',
'Back home: no accent':'بازگشت به خانه: بدون لهجه',
'home':'خانه',
'Accent maps':'نقشه‌های لهجه',
'English accents':'لهجه‌های انگلیسی',
'Other languages':'زبان‌های دیگر',
'Lines':'خط‌ها',
'Script text':'متن اسکریپت',
'Paste or type the script':'اسکریپت را بچسبان یا بنویس',
'Play script':'پخش اسکریپت',
'Add a line below':'افزودن خط در پایین',
'Add a line spoken by the default voice':'افزودن خط با صدای پیش‌فرض',
'Delete line':'حذف خط',
'Back to lines':'بازگشت به خط‌ها',
'Voices and performance':'صداها و اجرا',
'Default voice for this script':'صدای پیش‌فرض این اسکریپت',
'Pick a voice in Cast first':'اول در «طراحی صدا» یک صدا انتخاب کن',
'No voice':'بدون صدا',
'Enter saves · Esc cancels':'Enter ذخیره می‌کند · Esc لغو می‌کند',
'Text can’t be empty — Esc to cancel or delete the line':'متن نمی‌تواند خالی باشد — Esc برای لغو یا حذف خط',
'One line per row. Rows you leave unchanged keep their paint, voice and takes.':'هر خط یک ردیف. ردیف‌هایی که تغییر ندهی رنگ، صدا و اجراهایشان حفظ می‌شود.',
'The script is split at line breaks and sentence ends.':'اسکریپت در انتهای خط و جمله تقسیم می‌شود.',
'Remove every painted value from these words':'حذف همهٔ مقادیر رنگ‌شده از این کلمات',
'Settings or a voice changed since these lines’ keepers: render a new take of each':'تنظیمات یا صدا از زمان برداشت‌های نگه‌داشته‌شدهٔ این خط‌ها تغییر کرده: از هر کدام یک برداشت جدید رندر کن',
'Save the Cast voice as a cast member, so lines can pick it':'صدای «طراحی صدا» را به‌عنوان عضو گروه ذخیره کن تا خط‌ها بتوانند آن را انتخاب کنند',
'Delivery painted by reading position':'اجرا بر اساس موقعیت خواندن رنگ شده',
'Delivery painted by estimate (no alignment heads for this language)':'اجرا با تخمین رنگ شد (برای این زبان سر تراز نیست)',
'Not rendered: fix the pronunciation marked above.':'رندر نشده: تلفظ علامت‌گذاری‌شدهٔ بالا را اصلاح کن.',
'Edit as text':'ویرایش به‌صورت متن',
'Discard your script text changes?':'تغییرات متن اسکریپت را دور بریزی؟',
'Move down':'جابه‌جا به پایین',
'Move up':'جابه‌جا به بالا',
'Render':'رندر',
'Render a line first':'اول یک خط را رندر کن',
'Render every line that has no take yet':'رندر همهٔ خط‌هایی که هنوز اجرا ندارند',
'Render this line':'رندر این خط',
'Render another take (new seed)':'رندر اجرای دیگر (بذر جدید)',
'Stop rendering this line':'توقف رندر این خط',
'New take':'برداشت جدید',
'Take':'برداشت',
'Voice':'صدا',
'Energy':'انرژی',
'Gap between lines':'فاصلهٔ بین خط‌ها',
'Delivery brushes':'قلم‌موهای اجرا',
'Close brushes':'بستن قلم‌موها',
'Apply':'اعمال',
'Show':'نمایش',
'Edit the words of this take (Edit screen)':'ویرایش کلمات این برداشت (صفحهٔ ویرایش)',
'The line changed since this take was rendered; Regenerate makes a fresh one':'خط از زمانی که این برداشت رندر شد تغییر کرده؛ «تولید دوباره» یک تازه می‌سازد',
'Mixed: these words carry different values; moving the slider sets them all':'مختلط: این کلمات مقادیر متفاوتی دارند؛ جابه‌جایی اسلایدر همه را یکسان می‌کند',
'Paint delivery onto this line (or select words in it)':'اجرا را روی این خط رنگ کن (یا کلمات داخلش را انتخاب کن)',
'Edit text (paint stays on its words) · double-click the line':'ویرایش متن (رنگ روی کلماتش می‌ماند) · دوبار کلیک روی خط',
'Starts at once, no lead-in silence (click to allow it)':'فوراً شروع می‌شود، بدون سکوت ابتدایی (برای اجازه کلیک کن)',
'Script plays this one':'اسکریپت همین را پخش می‌کند',
'/ə/ Pronounce':'/ə/ تلفظ',
'Transcript':'رونوشت',
'Transcript mode':'حالت رونوشت',
'Transcript aligned: edit the words to change the speech':'رونوشت تراز شد: کلمات را ویرایش کن تا گفتار تغییر کند',
'Transcript corrected and re-aligned':'رونوشت اصلاح و دوباره تراز شد',
'Transcribing…':'در حال رونویسی…',
'Aligning the transcript…':'در حال تراز رونوشت…',
'Re-aligning…':'در حال تراز دوباره…',
'Keeping the take…':'در حال نگه‌داشتن برداشت…',
'Change the text':'تغییر متن',
'Go to Direct':'رفتن به «متن»',
'Leave the transcript fix? Your edited words are kept.':'اصلاح رونوشت را ترک کنی؟ کلمات ویرایش‌شده‌ات حفظ می‌شوند.',
'Changing the version discards your unsaved text and regions. Continue?':'تغییر نسخه، متن و نواحی ذخیره‌نشده‌ات را دور می‌ریزد. ادامه می‌دهی؟',
'All words heard':'همهٔ کلمات شنیده شد',
'Words of your text speech recognition did not hear in this take':'کلمات متن تو که تشخیص گفتار در این برداشت نشنید',
'Speech recognition heard every word of your text':'تشخیص گفتار همهٔ کلمات متن تو را شنید',
'No words were recognized in this clip.':'هیچ کلمه‌ای در این کلیپ تشخیص داده نشد.',
'No speech recognition on this server: listen to it':'تشخیص گفتار روی این سرور نیست: به آن گوش بده',
'This server can’t transcribe speech.':'این سرور نمی‌تواند گفتار را رونویسی کند.',
'No transcript: it will be transcribed':'رونوشت نیست: رونویسی می‌شود',
'No transcript: this server can’t transcribe, so you type what the clip says':'رونوشت نیست: این سرور نمی‌تواند رونویسی کند، پس خودت بنویس کلیپ چه می‌گوید',
'Type what is actually said in the audio. Low-confidence words are underlined in the waveform’s word lane.':'بنویس در صدا واقعاً چه گفته می‌شود. کلمات با اطمینان پایین در خط کلمات موج، زیرخط‌دار می‌شوند.',
'Type what is said in the audio, word for word.':'بنویس در صدا چه گفته می‌شود، کلمه‌به‌کلمه.',
'Type what is said in the clip…':'بنویس در کلیپ چه گفته می‌شود…',
'Type what the clip says':'بنویس کلیپ چه می‌گوید',
'Type what the clip says first: edits are planned against it':'اول بنویس کلیپ چه می‌گوید: ویرایش‌ها بر اساس آن برنامه‌ریزی می‌شوند',
'Type what is said in the clip; the audio is aligned to it':'بنویس در کلیپ چه گفته می‌شود؛ صدا با آن تراز می‌شود',
'What the clip says':'کلیپ چه می‌گوید',
'What speech recognition heard; differences from your text are marked':'تشخیص گفتار چه شنید؛ تفاوت‌ها با متن تو علامت‌گذاری شده‌اند',
'What speech recognition wrote where each pronunciation is (any word counts as said)':'تشخیص گفتار در جای هر تلفظ چه نوشت (هر کلمه‌ای به‌حساب گفته‌شده می‌آید)',
'Language of the clip':'زبان کلیپ',
'Correct transcript of the audio':'رونوشت صحیح صدا',
'Edited transcript: change the words to change the speech':'رونوشت ویرایش‌شده: کلمات را تغییر بده تا گفتار تغییر کند',
'Put the original words back into the text':'کلمات اصلی را به متن برگردان',
'How this take was directed':'این برداشت چطور کارگردانی شد',
'Current version':'نسخهٔ فعلی',
'Versions':'نسخه‌ها',
'Sources':'منابع',
'Sources and versions':'منابع و نسخه‌ها',
'Step to this version':'رفتن به این نسخه',
'Add this version to the Direct line as a new keeper take':'افزودن این نسخه به خط «متن» به‌عنوان برداشت نگه‌داشته‌شدهٔ جدید',
'Back to the transcript of this version':'بازگشت به رونوشت این نسخه',
'Drag on the waveform to re-say a stretch · drag a word’s edge to fix its timing':'روی موج بکش تا بخشی را دوباره بگویی · لبهٔ یک کلمه را بکش تا زمان‌بندی‌اش را اصلاح کنی',
'Drag to move the end':'بکش تا پایان را جابه‌جا کنی',
'Drag to move the start':'بکش تا شروع را جابه‌جا کنی',
'Drop to edit this clip':'رها کن تا این کلیپ را ویرایش کنی',
'Each change is widened by this much on both sides so the join sounds smooth':'هر تغییر به این اندازه از دو طرف گسترده می‌شود تا اتصال روان به نظر برسد',
'Word timings: drag word edges to correct them':'زمان‌بندی کلمات: لبه‌های کلمات را بکش تا اصلاح شوند',
'Word timings: finish the transcript first':'زمان‌بندی کلمات: اول رونوشت را تمام کن',
'Word timings: switch to A to edit':'زمان‌بندی کلمات: برای ویرایش به A برو',
'Recording':'ضبط',
'Voice clip':'کلیپ صدا',
'Direct take':'برداشت متن',
'The original take is gone from Direct':'برداشت اصلی از «متن» حذف شده',
'The server refused this edit':'سرور این ویرایش را رد کرد',
'Wait for the current step…':'منتظر مرحلهٔ فعلی بمان…',
'Start word playback 0.3 s early':'پخش کلمه را ۰٫۳ ثانیه زودتر شروع کن',
'Play with 1 s of pre-roll':'پخش با ۱ ثانیه پیش‌پخش',
'Not checked':'بررسی نشده',
'Fine-tune strength':'قدرت تنظیم دقیق',
'Generate':'تولید',
'Generating…':'در حال تولید…',
'Generation failed':'تولید ناموفق بود',
'Generate (⌘↵ / Ctrl+Enter)':'تولید (⌘↵ / Ctrl+Enter)',
'Generate again with this take\'s seed (Advanced → Seed)':'تولید دوباره با بذر این برداشت (پیشرفته، بعد بذر)',
'Keep: becomes the new version (K)':'نگه‌داشتن: نسخهٔ جدید می‌شود (K)',
'Select (A/B compares it with the version)':'انتخاب (A/B آن را با نسخه مقایسه می‌کند)',
'Play / pause (Space)':'پخش / مکث (Space)',
'Fit (0)':'جای‌دادن (0)',
'Zoom in (])':'بزرگ‌نمایی (])',
'Zoom in (]) · ⌘/Ctrl+wheel':'بزرگ‌نمایی (]) · ⌘/Ctrl+wheel',
'Zoom out ([)':'کوچک‌نمایی ([)',
'The current version (A)':'نسخهٔ فعلی (A)',
'The selected take (B)':'برداشت انتخاب‌شده (B)',
'comparing (B)':'در حال مقایسه (B)',
'Back to the alignment\'s word timings (undoable)':'بازگشت به زمان‌بندی کلمات تراز (قابل واگرد)',
'Correct what was actually said; the audio is re-aligned to it':'اصلاح کن واقعاً چه گفته شد؛ صدا دوباره با آن تراز می‌شود',
'An audio or video file; only the words you change are regenerated':'یک فایل صوتی یا ویدیویی؛ فقط کلماتی که تغییر می‌دهی دوباره تولید می‌شوند',
'A fixed seed repeats a take\'s randomness; empty picks one each time':'یک بذر ثابت، تصادفی‌بودن اجرا را تکرار می‌کند؛ خالی بودن، هر بار یکی انتخاب می‌کند',
'Long clips are edited on a window around the changes; this is how much audio the model listens to on each side':'کلیپ‌های بلند روی پنجره‌ای دور تغییرات ویرایش می‌شوند؛ این مقدار صدایی است که مدل از هر طرف گوش می‌دهد',
'Takes appear here. Each one is checked by speech recognition; the closest to your text is picked for you.':'اجراها اینجا ظاهر می‌شوند. هر کدام با تشخیص گفتار بررسی می‌شود؛ نزدیک‌ترین به متن تو برایت انتخاب می‌شود.',
'Takes appear here. Listen to each one and keep the best (this server can’t check takes with speech recognition).':'اجراها اینجا ظاهر می‌شوند. به هر کدام گوش بده و بهترین را نگه دار (این سرور نمی‌تواند اجراها را با تشخیص گفتار بررسی کند).',
'The clip\'s language: alignment uses it (switching aligns the transcript again)':'زبان کلیپ: تراز از آن استفاده می‌کند (تغییر، رونوشت را دوباره تراز می‌کند)',
'The clip\'s language: transcription, alignment and the take check use its speech model (the first use of a language loads it, about half a minute)':'زبان کلیپ: رونویسی، تراز و بررسی اجرا از مدل گفتاری آن استفاده می‌کنند (اولین استفاده از یک زبان آن را بارگذاری می‌کند، حدود نیم دقیقه)',
'Fixture mode: started with ?edit=0.':'حالت نمایشی: با ?edit=0 شروع شد.',
'Zoom':'بزرگ‌نمایی',
'Zoom to this region':'بزرگ‌نمایی این ناحیه',
'Compare':'مقایسه',
'Pause':'مکث',
'Remove this region':'برداشتن این ناحیه',
'· directed':'· کارگردانی‌شده',
', or':'، یا',
's per side':'ثانیه در هر طرف',
'Server':'سرور',
'Voice bundle':'بستهٔ صدا',
'Speech editing':'ویرایش گفتار',
'Speech recognition':'تشخیص گفتار',
'Pronunciation input':'ورودی تلفظ',
'Projects on the server':'پروژه‌ها روی سرور',
'In this browser':'در این مرورگر',
'Server · controls off':'سرور · کنترل‌ها خاموش',
'Demo audio':'صدای نمایشی',
'Demo audio: no ZONOS server answered — voices and takes are placeholders.':'صدای نمایشی: هیچ سرور ZONOS جواب نداد — صداها و برداشت‌ها جایگزین هستند.',
'Demo audio: no ZONOS server answered. Voices and takes are placeholders made in this browser (a babbling synthesizer that follows every control).':'صدای نمایشی: هیچ سرور ZONOS جواب نداد. صداها و برداشت‌ها جایگزین‌هایی هستند که در این مرورگر ساخته شده‌اند (یک سنتزکنندهٔ مبهم که از هر کنترل پیروی می‌کند).',
'Can’t reach the studio server':'نمی‌توان به سرور استودیو رسید',
'Connection':'اتصال',
'Close (Esc)':'بستن (Esc)',
'Close':'بستن',
'The voice engine runs in this tab on your graphics card; its models are cached in the browser.':'موتور صدا در این تب روی کارت گرافیک تو اجرا می‌شود؛ مدل‌هایش در مرورگر کش شده‌اند.',
'A ZONOS server renders every voice; controls and painted words are applied there.':'یک سرور ZONOS همهٔ صداها را رندر می‌کند؛ کنترل‌ها و کلمات رنگ‌شده آنجا اعمال می‌شوند.',
'This server can’t apply controls: renders are sent without them, so you hear each base voice as is.':'این سرور نمی‌تواند کنترل‌ها را اعمال کند: رندرها بدون آن‌ها فرستاده می‌شوند، پس هر صدای پایه را همان‌طور که هست می‌شنوی.',
'This server can’t apply controls: you hear each base voice as is.':'این سرور نمی‌تواند کنترل‌ها را اعمال کند: هر صدای پایه را همان‌طور که هست می‌شنوی.',
'What this studio is connected to':'این استودیو به چه متصل است',
'The in-browser engine couldn’t start':'موتور داخل مرورگر نتوانست شروع شود',
'Starting the voice engine…':'در حال شروع موتور صدا…',
'Opening the studio…':'در حال باز کردن استودیو…',
'Dark theme':'تم تیره',
'Light theme':'تم روشن',
'Switch to the dark theme':'تغییر به تم تیره',
'Switch to the light theme':'تغییر به تم روشن',
'More':'بیشتر',
'Home':'خانه',
'End':'پایان',
'Tab':'تب',
'Tab / ⇧Tab':'تب / ⇧تب',
'Space':'فاصله',
'Arrows':'کلیدهای جهت‌دار',
'Home / End':'خانه / پایان',
'0 / Delete':'۰ / حذف',
'double-click':'دوبار کلیک',
'scroll on the Who map':'اسکرول روی نقشهٔ Who',
'⌥-drag an edge':'⌥-کشیدن لبه',
'open a gap at a shared boundary':'باز کردن فاصله در مرز مشترک',
'Acts':'بخش‌ها',
'Undo':'بازگردانی',
'Redo':'تکرار',
'Dismiss':'رد کردن',
'Continue':'ادامه',
'Leave':'ترک',
'Open':'باز کن',
'Open larger':'بزرگ‌تر باز کن',
'Files':'فایل‌ها',
'Move':'جابه‌جا کن',
'Original':'اصلی',
'this browser':'این مرورگر',
'none':'هیچ',
'yes':'بله',
'no':'خیر',
'play / stop':'پخش / توقف',
'play / stop the script':'پخش / توقف اسکریپت',
'play / pause from the cursor':'پخش / مکث از مکان‌نما',
'hear the design / the original voice (Cast)':'شنیدن طرح / صدای اصلی (طراحی صدا)',
'undo':'واگرد',
'redo':'بازگرد',
'Cast, Direct, Edit':'طراحی صدا، متن، ویرایش',
'Cast: speak the text · new line in it':'طراحی صدا: متن را بخوان · خط جدید در آن',
'nudge the focused control (⇧ for more)':'جابه‌جایی کنترل متمرکز (⇧ برای بیشتر)',
'a slider to its minimum / maximum':'اسلایدر تا کمترین / بیشترین مقدار',
'reset the focused control':'بازنشانی کنترل متمرکز',
'reset a slider or the mood pad':'بازنشانی اسلایدر یا صفحهٔ حالت',
'pitch (the map focused, or with ⌘ / Ctrl)':'زیر و بمی (با تمرکز روی نقشه، یا با ⌘ / Ctrl)',
'a mood landmark: stronger, weaker, off':'نشانهٔ حالت: قوی‌تر، ضعیف‌تر، خاموش',
'X-SAMPA pronunciation in a line (phoneme-token checkpoints); click its IPA chip to edit':'تلفظ X-SAMPA در یک خط (نقاط وارسی توکن واج)؛ برای ویرایش روی چیپ IPA آن کلیک کن',
'look up the selected words’ pronunciations':'جست‌وجوی تلفظ کلمات انتخاب‌شده',
'this sheet':'این برگه',
'render the focused line':'رندر خط متمرکز',
'star the playing take (the script plays it)':'ستاره‌دار کردن برداشت در حال پخش (اسکریپت آن را پخش می‌کند)',
'save the line text while editing':'ذخیرهٔ متن خط هنگام ویرایش',
'paint the selected words · ⇧← / ⇧→ extend the selection':'رنگ کردن کلمات انتخاب‌شده · ⇧← / ⇧→ گسترش انتخاب',
'the version / the selected take':'نسخه / برداشت انتخاب‌شده',
'generate takes (also while typing)':'تولید برداشت‌ها (همچنین هنگام تایپ)',
'keep the selected take as a new version':'نگه‌داشتن برداشت انتخاب‌شده به‌عنوان نسخهٔ جدید',
'step through versions':'گذر از نسخه‌ها',
'zoom the waveform out / in · fit':'کوچک/بزرگ کردن موج · جای‌دادن',
'zoom around the pointer':'بزرگ‌نمایی حول نشانگر',
'next / previous word (from the transcript or a word)':'کلمهٔ بعدی / قبلی (از رونوشت یا یک کلمه)',
'select it and hear it (pre-roll optional)':'آن را انتخاب و گوش بده (پیش‌پخش اختیاری)',
'← / → (word selected)':'← / → (کلمه انتخاب‌شده)',
'Project':'پروژه',
'Project name':'نام پروژه',
'Recent projects':'پروژه‌های اخیر',
'The open project':'پروژهٔ باز',
'No saved projects.':'پروژهٔ ذخیره‌شده‌ای نیست.',
'Close projects':'بستن پروژه‌ها',
'Changed elsewhere':'جای دیگر تغییر کرد',
'It was saved by a newer version of the studio':'با نسخهٔ جدیدتری از استودیو ذخیره شده',
'Not a Voice Studio project file':'فایل پروژهٔ استودیو صدا نیست',
'Not a Voice Studio project file (not a zip)':'فایل پروژهٔ استودیو صدا نیست (zip نیست)',
'Projects are unavailable.':'پروژه‌ها در دسترس نیستند.',
'Reopen project':'بازگشایی پروژه',
'Voices that couldn’t be restored':'صداهایی که نتوانستند بازیابی شوند',
'Moving…':'در حال جابه‌جایی…',
'Not saved':'ذخیره نشده',
'Not saved — storage unavailable':'ذخیره نشده — حافظه در دسترس نیست',
'Rename':'تغییر نام',
'Try saving again':'دوباره ذخیره را امتحان کن',
'Export (zip)':'خروجی (zip)',
'Shared on the server: everyone with the studio link sees these':'روی سرور به اشتراک گذاشته شده: همهٔ کسانی که لینک استودیو را دارند این‌ها را می‌بینند',
'Saved in this browser · move them with Export / Import':'در این مرورگر ذخیره شده · با خروجی / ورودی جابه‌جاشان کن',
'Site storage used':'حافظهٔ سایت مصرف‌شده',
'A project is':'یک پروژه هنوز',
'still saved in this browser only.':'فقط در این مرورگر ذخیره شده است.',
'This project was changed elsewhere (another tab or browser) since you opened it. Reload it, or keep your version as a copy.':'این پروژه از زمانی که بازش کردی جای دیگری (تب یا مرورگر دیگر) تغییر کرده. آن را دوباره بارگذاری کن، یا نسخهٔ خودت را به‌عنوان کپی نگه دار.',
'This project was deleted elsewhere (another tab or browser). Keep your version as a copy to go on with it.':'این پروژه جای دیگری (تب یا مرورگر دیگر) حذف شده. نسخهٔ خودت را به‌عنوان کپی نگه دار تا ادامه بدهی.',
'open':'باز',
'Edit pronunciation':'ویرایش تلفظ',
'Pronounce the selected words':'تلفظ کلمات انتخاب‌شده',
'Pronounce…':'تلفظ…',
'Pronunciation:':'تلفظ:',
'Pronunciations':'تلفظ‌ها',
'Pronunciation lexicon language':'زبان واژه‌نامهٔ تلفظ',
'Or select words and press /ə/ to look them up.':'یا کلمات را انتخاب کن و /ə/ را بزن تا جستجو شوند.',
'This server has no pronunciation lexicon: parentheses are read as text.':'این سرور واژه‌نامهٔ تلفظ ندارد: پرانتزها به‌عنوان متن خوانده می‌شوند.',
'This server reads parentheses as plain text: it has no pronunciation input':'این سرور پرانتزها را به‌عنوان متن ساده می‌خواند: ورودی تلفظ ندارد',
'Write a pronunciation in X-SAMPA between parentheses: “I met':'تلفظ را با X-SAMPA بین پرانتز بنویس: “I met',
'” reads':'” خوانده می‌شود',
'( ) read as text':'( ) به‌عنوان متن خوانده می‌شود',
'check':'بررسی‌شده',
'empty':'خالی',
'Where /ə/ looks words up. It follows the project language until you pick another here (for this session)':'جایی که /ə/ کلمات را جستجو می‌کند. تا وقتی اینجا زبان دیگری انتخاب کنی، از زبان پروژه پیروی می‌کند (برای این نشست)',
'· strong past that':'· قوی، بعد از آن',
'Click the pad to open the mood controls':'روی صفحه کلیک کن تا کنترل‌های حالت باز شوند',
'Delete this take':'حذف این برداشت',
'Number of takes':'تعداد برداشت‌ها',
'Allow the microphone in the browser’s site settings':'اجازهٔ میکروفون را در تنظیمات سایت مرورگر بده',
'clear':'پاک کردن',
};

var RDICT={};for(var k in DICT){if(!RDICT[DICT[k]])RDICT[DICT[k]]=k;}
var SKIP_TAGS={SCRIPT:1,STYLE:1,INPUT:1,TEXTAREA:1,CANVAS:1,SVG:1};
var SKIP_SEL='.line-main,.script-text,.take-asr,.take-heard,.voice-label,.voice-persona,[contenteditable="true"]';
function skipEl(el){if(!el)return true;var t=el.tagName;if(SKIP_TAGS[t])return true;try{if(el.closest(SKIP_SEL))return true;}catch(e){}return false;}
function trNode(n,map){var t=n.nodeValue;if(!t)return;var c=t.trim();if(!c)return;var v=map[c];if(v===undefined)return;n.nodeValue=t.replace(c,v);}
function trAttrs(el,map){var attrs=['title','placeholder','aria-label'];for(var i=0;i<attrs.length;i++){var v=el.getAttribute&&el.getAttribute(attrs[i]);if(!v)continue;var c=v.trim();var r=map[c];if(r!==undefined)el.setAttribute(attrs[i],r);}}
function walk(root,map){var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null);var n;var nodes=[];while((n=w.nextNode()))nodes.push(n);for(var i=0;i<nodes.length;i++){if(!skipEl(nodes[i].parentElement))trNode(nodes[i],map);}var els=root.querySelectorAll?root.querySelectorAll('[title],[placeholder],[aria-label]'):[];for(var j=0;j<els.length;j++){if(!els[j].closest||!els[j].closest(SKIP_SEL))trAttrs(els[j],map);}}
function applyLang(lang){
window.__zonoStudioLang=lang;
var btn=document.getElementById('zono-lang-toggle');
if(btn)btn.textContent=(lang==='fa'?'EN':'فا');
var map=(lang==='fa')?DICT:RDICT;
walk(document.body||document.documentElement,map);
}
window.__zonoSetStudioLang=function(lang){try{applyLang(lang);}catch(e){}};
function ensureToggle(){
if(document.getElementById('zono-lang-toggle'))return;
var host=document.querySelector('.topbar-right')||document.querySelector('.topbar');
if(!host)return;
var b=document.createElement('button');
b.id='zono-lang-toggle';b.type='button';
b.textContent='EN';
b.setAttribute('aria-label','Language');
b.style.cssText='padding:4px 10px;border:1px solid currentColor;border-radius:999px;background:transparent;color:inherit;font-size:12px;line-height:1.4;cursor:pointer;flex:none;';
b.addEventListener('click',function(){
var next=(window.__zonoStudioLang==='fa')?'en':'fa';
try{window.__zonoSetStudioLang(next);}catch(e){}
try{window.ZonoNative.onEvent(JSON.stringify({type:'studioLang',lang:next}));}catch(e){}
});
host.appendChild(b);
}
ensureToggle();
applyLang(window.__zonoStudioLang||'en');
if(!window.__zonoFaObserver){
try{
var obs=new MutationObserver(function(muts){
ensureToggle();
var lang=window.__zonoStudioLang||'en';
var map=(lang==='fa')?DICT:RDICT;
for(var i=0;i<muts.length;i++){
var m=muts[i];
if(m.type==='characterData'){if(!skipEl(m.target.parentElement))trNode(m.target,map);continue;}
var added=m.addedNodes;
for(var j=0;j<added.length;j++){
var nd=added[j];
if(nd.nodeType===3){if(!skipEl(nd.parentElement))trNode(nd,map);}
else if(nd.nodeType===1){if(nd.id==='zono-lang-toggle')continue;walk(nd,map);}
}
}
});
var tgt=document.body||document.documentElement;
if(tgt){obs.observe(tgt,{childList:true,subtree:true,characterData:true});window.__zonoFaObserver=obs;}
}catch(e){}
}
}catch(e){}})()
"""

        /** SharedPreferences key for the Studio language ("fa" / "en"). */
        const val STUDIO_LANG_KEY = "studio_lang"

        /** The persisted Studio language, or [default] when none was saved. */
        fun loadStudioLang(context: Context, default: String): String {
            val raw = context.getSharedPreferences(ModelsLocation.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(STUDIO_LANG_KEY, null)
            return normalizeStudioLang(raw, normalizeStudioLang(default))
        }

        fun saveStudioLang(context: Context, lang: String) {
            context.getSharedPreferences(ModelsLocation.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(STUDIO_LANG_KEY, normalizeStudioLang(lang)).apply()
        }
    }

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val _coreMissing = MutableStateFlow(false)
    /** True when the Studio was opened without the CORE pack installed. */
    val coreMissing: StateFlow<Boolean> = _coreMissing

    /** Technical detail of the last export attempt (null = nothing yet). */
    private val _lastExport = MutableStateFlow<ExportOutcome?>(null)
    val lastExport: StateFlow<ExportOutcome?> = _lastExport

    /** Studio UI language used when the learner never flipped the toggle ("en" or "fa"). */
    @Volatile
    var defaultLang: String = "en"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Set by the Compose layer: launch the SAF picker for <input type=file>. */
    var onFileChooser: ((ValueCallback<Array<Uri>>, Intent) -> Unit)? = null

    private var attachedWv: WebView? = null
    private var attachedActivity: Activity? = null
    private var lastUrl: String? = null

    /**
     * Attach (or re-attach) the WebView, built with the Activity's
     * context (system <select> popups cannot open from an application
     * context). Same Activity -> the existing instance; otherwise a fresh
     * WebView that replays the pending load.
     */
    fun attachedWebView(context: Context): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "attachedWebView must run on the main thread"
        }
        val activity = requireNotNull(context.findActivity()) {
            "StudioEngine WebView needs an Activity context"
        }
        val existing = attachedWv
        if (existing != null && attachedActivity === activity) {
            // Re-attach to a new Compose host: the previous AndroidView
            // holder still owns the view until it is removed from it.
            try { (existing.parent as? ViewGroup)?.removeView(existing) } catch (_: Exception) { }
            return existing
        }
        if (existing != null) destroyAttached()
        val wv = buildWebView(activity)
        attachedWv = wv
        attachedActivity = activity
        if (loaded) {
            if (ModelPackManager(context).coreInstalled()) {
                _state.value = _state.value.copy(status = EngineStatus.LOADING)
            }
            lastUrl?.let { wv.loadUrl(it) }
        }
        return wv
    }

    /** Detach from the Compose tree without destroying (the WebView is reused on the next attach). */
    fun detachFromParent() {
        val wv = attachedWv ?: return
        try { (wv.parent as? ViewGroup)?.removeView(wv) } catch (_: Exception) { }
    }

    /** Tear the WebView down (Activity finishing). */
    fun release() {
        destroyAttached()
        loaded = false
        _state.value = EngineState()
    }

    private fun destroyAttached() {
        val old = attachedWv ?: return
        attachedWv = null
        attachedActivity = null
        try { old.stopLoading() } catch (_: Exception) { }
        try { (old.parent as? ViewGroup)?.removeView(old) } catch (_: Exception) { }
        try { old.destroy() } catch (_: Exception) { }
    }

    @Volatile
    var loaded: Boolean = false
        private set

    private val errorHandlersJs = "(function(){" +
        "if(window.__zonoDiagInstalled)return;window.__zonoDiagInstalled=true;" +
        "function fwd(m,s,l){try{window.ZonoNative.onEvent(JSON.stringify({type:'pageerror',message:String(m),source:String(s||''),line:(l||0)}))}catch(_){}};" +
        "window.onerror=function(m,s,l,c,e){fwd(m,s,l);return false;};" +
        "window.addEventListener('unhandledrejection',function(e){var r=e.reason;fwd('unhandledrejection: '+(r&&r.message?r.message:String(r)),'',0);});" +
        "window.addEventListener('error',function(e){var t=e.target||{};var src=t.src||t.href||'';fwd(e.message||('load failed: '+src),e.filename||src,e.lineno||0);},true);" +
        "})()"

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(activityContext: Context): WebView {
        check(activityContext is Activity) { "WebView requires an Activity context" }
        val wv = WebView(activityContext)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.allowFileAccess = false
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                    !message.message().contains("favicon", ignoreCase = true)
                ) {
                    val detail = "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}"
                    _state.value = _state.value.copy(lastConsoleError = detail)
                }
                return super.onConsoleMessage(message)
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val handler = onFileChooser ?: return false
                return try {
                    handler(filePathCallback, fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val res = ModelFileServer.intercept(context, request.url, request.method, request.requestHeaders)
                    ?: loader.shouldInterceptRequest(request.url)
                val err = ModelFileServer.lastError
                if (_state.value.lastInterceptorError != err) {
                    _state.value = _state.value.copy(lastInterceptorError = err)
                }
                return res
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(STUDIO_EXPORT_HOOK_JS, null)
                _state.value = _state.value.copy(status = EngineStatus.READY, pageLoaded = true)
                injectStudioHeights()
                injectStudioTranslation()
                view.evaluateJavascript(errorHandlersJs, null)
            }
        }
        wv.addJavascriptInterface(StudioBridge(), "ZonoNative")
        return wv
    }

    /** Apply the pixel height fix; idempotent, safe to repeat (also on resize). */
    fun injectStudioHeights() {
        val view = attachedWv ?: return
        view.post { view.evaluateJavascript(STUDIO_PIXEL_HEIGHT_FIX_JS, null) }
    }

    private fun injectStudioTranslation() {
        val view = attachedWv ?: return
        view.post {
            val lang = loadStudioLang(context, defaultLang)
            view.evaluateJavascript("window.__zonoStudioLang='$lang';", null)
            view.evaluateJavascript(STUDIO_FA_TRANSLATE_JS, null)
            view.evaluateJavascript("try{window.__zonoSetStudioLang('$lang')}catch(e){}", null)
        }
    }

    /** Load the Studio once (URL depends on whether CORE is installed). */
    fun load() {
        if (loaded) return
        val core = ModelPackManager(context).coreInstalled()
        _coreMissing.value = !core
        _state.value = EngineState(status = if (core) EngineStatus.LOADING else EngineStatus.NO_MODEL)
        val url = studioUrl(core)
        lastUrl = url
        attachedWv?.loadUrl(url)
        loaded = true
    }

    /**
     * Re-check the CORE gate after a pack install/delete. If CORE just
     * arrived, reload the Studio with the ?api=web URL.
     */
    fun refreshGate() {
        val core = ModelPackManager(context).coreInstalled()
        _coreMissing.value = !core
        if (core && !loaded) {
            load()
        } else if (core && loaded && _state.value.status == EngineStatus.NO_MODEL) {
            loaded = false
            load()
        }
    }

    fun clearModelCache() {
        val view = attachedWv ?: return
        view.post {
            view.evaluateJavascript("try{caches.delete('${ZonoEngine.MODEL_CACHE_NAME}')}catch(e){}", null)
        }
    }

    private inner class StudioBridge {
        @JavascriptInterface
        fun onEvent(json: String) {
            scope.launch {
                try {
                    val o = JSONObject(json)
                    when (o.optString("type")) {
                        "pageerror" -> {
                            val detail = o.optString("message") +
                                (o.optString("source").takeIf { it.isNotEmpty() }
                                    ?.let { " @ $it:${o.optInt("line", 0)}" } ?: "")
                            if (detail.isNotBlank() && !detail.contains("favicon", ignoreCase = true)) {
                                _state.value = _state.value.copy(lastPageError = detail)
                            }
                        }
                        "studioLang" -> saveStudioLang(context, o.optString("lang", defaultLang))
                        "exportData" -> {
                            val name = o.optString("name", "studio-export.wav")
                            val b64 = o.optString("base64", "")
                            val safe = ExportSaver.sanitizeName(name)
                            val outcome = withContext(Dispatchers.IO) {
                                try {
                                    if (b64.isBlank()) throw IllegalStateException("empty export data")
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val where = ExportSaver.saveBytes(context, safe, bytes)
                                    ExportOutcome(name = safe, bytes = bytes.size.toLong(), destination = where)
                                } catch (e: Exception) {
                                    ExportOutcome(name = safe, bytes = 0, destination = "", error = e.message ?: e.javaClass.simpleName)
                                }
                            }
                            _lastExport.value = outcome
                        }
                        "exportError" -> {
                            _lastExport.value = ExportOutcome(
                                name = ExportSaver.sanitizeName(o.optString("name", "studio-export.wav")),
                                bytes = 0, destination = "",
                                error = o.optString("message", "export failed")
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Malformed event: ignore.
                }
            }
        }
    }
}

/** Outcome of a Studio export (download button inside the Studio page). */
data class ExportOutcome(
    val name: String,
    val bytes: Long,
    val destination: String,
    val error: String? = null
)
