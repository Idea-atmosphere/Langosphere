package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppStrings

/** One collapsible chapter of the in-app guide. */
private data class GuideTopic(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val points: List<String>
)

/**
 * Settings ▸ "App guide": a complete walkthrough of Langosphere, with one
 * separate chapter per tab and per section, so a new user never has to guess
 * what a button does. Only one chapter is open at a time to keep it readable.
 */
@Composable
fun AppGuideDialog(
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val isEn = strings.isEn
    val topics = remember(isEn) { buildGuideTopics(isEn) }
    var openTitle by remember(isEn) { mutableStateOf(topics.first().title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEn) "Langosphere guide" else "راهنمای Langosphere",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEn) {
                            "Every tab and every section, explained"
                        } else {
                            "توضیح هر تب و هر بخش، جداگانه"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.forEach { topic ->
                    GuideTopicCard(
                        topic = topic,
                        expanded = openTitle == topic.title,
                        onToggle = {
                            openTitle = if (openTitle == topic.title) "" else topic.title
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun GuideTopicCard(
    topic: GuideTopic,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "guideChevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (expanded) scheme.primary.copy(alpha = 0.10f)
                else scheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable(onClick = onToggle)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = if (expanded) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    topic.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.primary
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevron },
                tint = scheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                topic.points.forEach { point ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = scheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The guide text itself. Written out per language instead of going through
 * AppStrings so the guide can be extended without touching the shared string
 * table.
 */
private fun buildGuideTopics(isEn: Boolean): List<GuideTopic> = listOf(
    GuideTopic(
        icon = Icons.Filled.Check,
        title = if (isEn) "Quick start" else "شروع سریع",
        summary = if (isEn) "From an empty app to your first lesson" else "از صفر تا اولین درس",
        points = if (isEn) listOf(
            "1) Open the video tab, expand the import panel and pick a video or audio file.",
            "2) Load an English subtitle and a translated subtitle into their own slots. You can pick a file or paste a copied subtitle.",
            "3) Play. Tap any word for the dictionary, or tap the sentence itself for a full lesson about that line.",
            "4) Save new words to the Leitner box and review them in the Leitner tab.",
            "5) If you have an API key, add it in the AI tab to unlock automatic translation and lesson building."
        ) else listOf(
            "۱) به تب پخش‌کننده ویدیو برو، بخش افزودن فایل را باز کن و فیلم یا فایل صوتی را انتخاب کن.",
            "۲) زیرنویس انگلیسی و زیرنویس ترجمه را در خانه‌های جداگانه‌ی خودشان بارگذاری کن؛ هم می‌توانی فایل انتخاب کنی و هم متن کپی‌شده را جای‌گذاری کنی.",
            "۳) پخش را شروع کن. با زدن روی هر کلمه دیکشنری باز می‌شود و با زدن روی خودِ جمله، درس کاملِ آن جمله باز می‌شود.",
            "۴) کلمه‌های تازه را در جعبه لایتنر ذخیره کن و در تب لایتنر مرورشان کن.",
            "۵) اگر کلید API داری، در تب هوش مصنوعی واردش کن تا ترجمه و درس‌سازی خودکار فعال شود."
        )
    ),
    GuideTopic(
        icon = Icons.Outlined.MenuBook,
        title = if (isEn) "Reader tab" else "تب متن‌خوان",
        summary = if (isEn) "Read any text and tap words" else "خواندن متن و لمس کلمه‌ها",
        points = if (isEn) listOf(
            "Load a text file, or paste text, and read it in a clean full-width layout.",
            "Every word is tappable: one tap opens the dictionary popup for it, with the surrounding sentence as context.",
            "Words you save from here land in the same Leitner box as the ones you save from the video.",
            "The AI tab can rewrite or correct the reader text; it edits the very same text you are reading.",
            "Very large files are handled with the word limit in the settings menu, so the screen never freezes."
        ) else listOf(
            "یک فایل متنی را بارگذاری کن یا متن را جای‌گذاری کن و در یک صفحه‌ی تمیز بخوان.",
            "هر کلمه قابل لمس است: با یک ضربه، پاپ‌آپ دیکشنری همان کلمه با جمله‌ی اطرافش به‌عنوان زمینه باز می‌شود.",
            "کلمه‌هایی که از این‌جا ذخیره می‌کنی، در همان جعبه لایتنری می‌روند که کلمه‌های ویدیو در آن ذخیره می‌شوند.",
            "تب هوش مصنوعی می‌تواند همین متن را بازنویسی یا اصلاح کند؛ دقیقاً روی متنی کار می‌کند که در حال خواندنش هستی.",
            "برای فایل‌های خیلی بزرگ، «حداکثر تعداد کلمه» در منوی تنظیمات را تنظیم کن تا صفحه کند نشود."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.PlayArrow,
        title = if (isEn) "Video player tab" else "تب پخش‌کننده ویدیو",
        summary = if (isEn) "Player gestures, tools and study modes" else "حرکت‌ها، ابزارها و حالت‌های تمرین",
        points = if (isEn) listOf(
            "Import panel: the wide tile is the media file, the three small tiles are the English, translated and JSON subtitle slots. Tap the header to fold the panel; it also folds automatically when you scroll the subtitle list.",
            "Tap the picture once to show or hide the controls. Double-tap the left or right side to jump backwards or forwards by the skip amount set in the player settings.",
            "The three-dot button is the tool cluster: it unfolds player settings, A-B repeat, the subtitle switch, focus mode and fullscreen with one animation, so the picture stays clean.",
            "The speed pill next to it opens the study panel: playback speed, repeat this line, listen mode, coverage and pronunciation.",
            "A-B repeat: first tap marks the start, second tap marks the end, third tap turns the loop off. A banner shows the active loop.",
            "Smart pause: the player stops at the end of a line so you can read it. In this mode the screen stays flat with only a clock, no bottom bar.",
            "Listen mode hides the subtitles so you listen first; the reveal pill brings the current line back when you want to check yourself.",
            "Coverage shows what share of the words in this film you already know, plus the most frequent unknown words. Marking a word as known removes it from that list.",
            "If a subtitle is out of sync, use the sync section to shift it by fractions of a second. English, translated and JSON subtitles each shift independently.",
            "Drag the handle between the video and the subtitle list to change how tall the video is. Focus mode hides everything except the video and the subtitles.",
            "Your position in each file is remembered, so reopening a film continues where you left off."
        ) else listOf(
            "بخش افزودن فایل: خانه‌ی پهن برای فایل ویدیو یا صوت است و سه خانه‌ی کوچک برای زیرنویس انگلیسی، زیرنویس ترجمه و فایل JSON آموزشی. با زدن روی سرِ بخش جمع می‌شود و وقتی لیست زیرنویس را اسکرول کنی هم خودش جمع می‌شود.",
            "یک ضربه روی تصویر، کنترل‌ها را نشان می‌دهد یا پنهان می‌کند. دو ضربه روی سمت چپ یا راست، به اندازه‌ی مقدار پرش (در تنظیمات پخش‌کننده) عقب یا جلو می‌رود.",
            "دکمه‌ی سه‌نقطه، ابزارهای پخش‌کننده است: با یک انیمیشن، تنظیمات پخش، تکرار A-B، کلید زیرنویس، حالت تمرکز و تمام‌صفحه را باز می‌کند تا روی تصویر شلوغی نباشد.",
            "پیل سرعت کنارش، پنل تمرین را باز می‌کند: سرعت پخش، تکرار همین جمله، حالت گوش کن، درصد پوشش و تلفظ.",
            "تکرار A-B: ضربه‌ی اول نقطه‌ی شروع، ضربه‌ی دوم نقطه‌ی پایان و ضربه‌ی سوم خاموش کردن حلقه است. یک نوار کوچک، حلقه‌ی فعال را نشان می‌دهد.",
            "توقف هوشمند: پخش‌کننده در پایان هر جمله می‌ایستد تا فرصت خواندن داشته باشی. در این حالت صفحه صاف می‌ماند و فقط یک ساعت دیده می‌شود، بدون نوار پایین.",
            "حالت گوش کن، زیرنویس‌ها را پنهان می‌کند تا اول بشنوی؛ هر وقت خواستی خودت را بسنجی، با دکمه‌ی نمایش، جمله‌ی جاری برمی‌گردد.",
            "درصد پوشش نشان می‌دهد چند درصد کلمه‌های این فیلم را از قبل می‌دانی و پرتکرارترین کلمه‌های ناشناس را فهرست می‌کند. تا کلمه‌ای را «بلدم» علامت بزنی، از آن فهرست حذف می‌شود.",
            "اگر زیرنویس جلو یا عقب است، از بخش هم‌زمان‌سازی، آن را کسری از ثانیه جابه‌جا کن. زیرنویس انگلیسی، ترجمه و JSON هر کدام مستقل جابه‌جا می‌شوند.",
            "خط جداکننده‌ی بین ویدیو و لیست زیرنویس را بکش تا ارتفاع ویدیو را کم و زیاد کنی. حالت تمرکز هم همه چیز را جز ویدیو و زیرنویس پنهان می‌کند.",
            "محل پخش هر فایل ذخیره می‌شود؛ فیلم را که دوباره باز کنی، از همان‌جا ادامه می‌دهد."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Subtitles,
        title = if (isEn) "Subtitles and the JSON learning package" else "زیرنویس‌ها و بسته‌ی JSON آموزشی",
        summary = if (isEn) "Three subtitle slots and what each is for" else "سه خانه‌ی زیرنویس و کار هر کدام",
        points = if (isEn) listOf(
            "English slot: the original subtitle. Translated slot: the same film in your language. Together they build the two-line view.",
            "Each slot accepts a file from storage or a subtitle you copied to the clipboard.",
            "The JSON slot is the rich one: besides the translation it carries the lesson, grammar, structure, word list, pronunciation and examples for every line.",
            "Build that JSON with the prompt generator in Settings ▸ Tutorial & AI learning: choose a mode and a level, copy the prompt, run it on your subtitle in any AI chat, then paste the result back into the JSON slot.",
            "The paste dialog detects the format live and offers a sample JSON so you can test the import before doing the whole film.",
            "The dictionary-vs-JSON switch decides what a word tap opens when a JSON package is loaded: the offline dictionary, or the JSON explanation.",
            "\"Remove imported subtitles\" clears the English, translated and JSON data in one tap, and the player can export the current translation as an SRT file."
        ) else listOf(
            "خانه‌ی انگلیسی، زیرنویس اصلی است و خانه‌ی ترجمه، همان فیلم به زبان خودت. این دو با هم نمایش دوخطی را می‌سازند.",
            "هر خانه هم فایل از حافظه را می‌پذیرد و هم زیرنویسی که در کلیپ‌بورد کپی کرده‌ای.",
            "خانه‌ی JSON، بسته‌ی کامل است: کنار ترجمه، برای هر جمله درس، گرامر، ساختار، فهرست کلمه‌ها، تلفظ و مثال هم دارد.",
            "این فایل JSON را با پرامپت‌ساز در «تنظیمات ▸ آموزش و یادگیری AI» بساز: حالت و سطح را انتخاب کن، پرامپت را کپی کن، روی زیرنویس‌ات در هر چت هوش مصنوعی اجرا کن و نتیجه را در خانه‌ی JSON جای‌گذاری کن.",
            "پنجره‌ی جای‌گذاری، قالب را همان لحظه تشخیص می‌دهد و یک نمونه‌ی آماده هم دارد تا قبل از کل فیلم، ورود اطلاعات را آزمایش کنی.",
            "کلید «دیکشنری یا JSON» تعیین می‌کند وقتی بسته‌ی JSON بارگذاری شده، ضربه روی کلمه چه چیزی باز کند: دیکشنری آفلاین یا توضیح JSON.",
            "دکمه‌ی «حذف زیرنویس‌های وارد‌شده» با یک ضربه داده‌های انگلیسی، ترجمه و JSON را پاک می‌کند و پخش‌کننده هم می‌تواند ترجمه‌ی فعلی را به‌شکل فایل SRT بیرون بدهد."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Style,
        title = if (isEn) "Word and lesson popups" else "پاپ‌آپ کلمه و درس",
        summary = if (isEn) "What opens when you tap a word or a line" else "چه چیزی با لمس کلمه یا جمله باز می‌شود",
        points = if (isEn) listOf(
            "Tapping a word opens the dictionary popup: the entry itself, the English sentence it came from and its translation.",
            "The search box at the top lets you look up a different word without closing the popup.",
            "With three or more dictionaries imported, filter buttons appear so you can see which dictionary an entry came from.",
            "The pronunciation row speaks the word out loud, and the slow option repeats it at reduced speed.",
            "\"I know this\" marks the word as known, which feeds the coverage number in the player.",
            "Add to Leitner saves the word plus its definition as a flashcard.",
            "Tapping the sentence instead of a word opens the line lesson: meaning, grammar, structure, key words and examples, and every word in there is tappable too."
        ) else listOf(
            "با لمس یک کلمه، پاپ‌آپ دیکشنری باز می‌شود: خودِ مدخل، جمله‌ی انگلیسی‌ای که از آن آمده و ترجمه‌اش.",
            "جعبه‌ی جست‌وجوی بالای پاپ‌آپ، امکان می‌دهد بدون بستن پنجره، کلمه‌ی دیگری را هم ببینی.",
            "اگر سه دیکشنری یا بیشتر وارد کرده باشی، دکمه‌های فیلتر ظاهر می‌شوند تا ببینی هر مدخل از کدام دیکشنری آمده.",
            "ردیف تلفظ، کلمه را با صدا می‌خواند و گزینه‌ی آهسته، همان را با سرعت کمتر تکرار می‌کند.",
            "دکمه‌ی «بلدم» کلمه را دانسته علامت می‌زند و همین، عددِ درصد پوشش در پخش‌کننده را تغذیه می‌کند.",
            "دکمه‌ی افزودن به لایتنر، کلمه را همراه معنایش به‌شکل یک کارت ذخیره می‌کند.",
            "اگر به‌جای کلمه روی خودِ جمله بزنی، درس آن جمله باز می‌شود: معنا، گرامر، ساختار، کلمه‌های کلیدی و مثال‌ها؛ و همه‌ی کلمه‌های داخل آن هم قابل لمس‌اند."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Language,
        title = if (isEn) "AI tab" else "تب هوش مصنوعی",
        summary = if (isEn) "Connection, chat, memory and prompts" else "اتصال، گفت‌وگو، حافظه و پرامپت‌ها",
        points = if (isEn) listOf(
            "Open the settings card at the top and fill in the base URL, the API key, the model name and your target language. Any OpenAI-compatible endpoint works.",
            "The chat understands your loaded material: it can translate a subtitle, fix the translation, explain grammar or rewrite the reader text, and it can write those changes straight back into the app.",
            "Chat history is saved as sessions, so you can leave a conversation and come back to it; the bubble colours are yours to choose.",
            "The memory panel has separate tabs: prompts, corrections, skills and export/import.",
            "Prompts tab: every system prompt the app uses is editable, with the available variables shown as chips, a reset button, and a test run on three real lines so you see the effect before trusting it.",
            "Corrections and skills are what the app learned from you: each time you fix a translation, that pattern is remembered and sent along with future requests.",
            "Learning from subtitles or from the dictionary builds a glossary of your own; it makes later translations more consistent and much cheaper.",
            "Translation runs in batches with a cache, so identical lines are never paid for twice, and the progress can be stopped mid-run.",
            "Export/import writes the whole memory to a file, which is also the safest way to move it to another phone."
        ) else listOf(
            "کارت تنظیمات بالای صفحه را باز کن و آدرس پایه، کلید API، نام مدل و زبان مقصدت را وارد کن. هر سرویسی که با OpenAI سازگار باشد کار می‌کند.",
            "گفت‌وگو، فایل‌های بارگذاری‌شده‌ات را می‌شناسد: می‌تواند زیرنویس را ترجمه کند، ترجمه را اصلاح کند، گرامر را توضیح دهد یا متن متن‌خوان را بازنویسی کند و همین تغییرها را مستقیم در برنامه بنویسد.",
            "تاریخچه‌ی گفت‌وگو به‌شکل نشست‌های جدا ذخیره می‌شود تا بتوانی بعداً برگردی؛ رنگ حباب‌ها هم به انتخاب خودت است.",
            "پنل حافظه تب‌های جدا دارد: پرامپت‌ها، اصلاح‌ها، مهارت‌ها و گرفتن/برگرداندن نسخه‌ی پشتیبان.",
            "تب پرامپت‌ها: همه‌ی پرامپت‌های سیستمیِ برنامه قابل ویرایش‌اند؛ متغیرهای مجاز به‌شکل چیپ نشان داده می‌شوند، دکمه‌ی بازگشت به حالت اولیه هست و می‌توانی پرامپت را روی سه جمله‌ی واقعی آزمایش کنی تا قبل از اعتماد، نتیجه را ببینی.",
            "اصلاح‌ها و مهارت‌ها همان چیزهایی است که برنامه از تو یاد گرفته: هر بار ترجمه‌ای را درست می‌کنی، آن الگو به یاد می‌ماند و همراه درخواست‌های بعدی فرستاده می‌شود.",
            "یادگیری از زیرنویس یا از دیکشنری، یک واژه‌نامه‌ی مخصوص خودت می‌سازد؛ همین باعث می‌شود ترجمه‌های بعدی یکدست‌تر و بسیار کم‌هزینه‌تر شوند.",
            "ترجمه به‌صورت بسته‌بسته و با حافظه‌ی موقت اجرا می‌شود، پس برای جمله‌های تکراری دو بار هزینه نمی‌دهی و می‌توانی وسط کار متوقفش کنی.",
            "گرفتن نسخه‌ی پشتیبان، تمام حافظه را در یک فایل می‌نویسد و مطمئن‌ترین راه برای بردن آن به گوشی دیگر است."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Style,
        title = if (isEn) "Leitner box tab" else "تب جعبه لایتنر",
        summary = if (isEn) "Spaced repetition for your saved words" else "مرور فاصله‌دار کلمه‌های ذخیره‌شده",
        points = if (isEn) listOf(
            "Cards come from the word popup; this tab only reviews and manages them.",
            "Five boxes: a card you answer correctly climbs one box and comes back later, a card you miss falls back and returns sooner. A card in the last box counts as mastered, which is the percentage in the ring.",
            "Switch between \"review today\" and \"all cards\" with the two pills.",
            "Swipe left or right to move between today's cards; the pill on the card shows which card you are on and how many there are in total.",
            "Tap the card to flip it in 3D and reveal the meaning; the answer buttons only appear once it is face-up. Long definitions scroll inside the card.",
            "The dots on the card show the box level without any extra text.",
            "The download button exports every card as an Anki-compatible file."
        ) else listOf(
            "کارت‌ها از پاپ‌آپ کلمه می‌آیند؛ این تب فقط آن‌ها را مرور و مدیریت می‌کند.",
            "پنج جعبه: کارتی که درست جواب بدهی یک جعبه بالا می‌رود و دیرتر برمی‌گردد، کارتی که بلد نباشی پایین می‌آید و زودتر برمی‌گردد. کارت جعبه‌ی آخر «تسلط» حساب می‌شود و همان درصدِ داخل حلقه است.",
            "با دو پیل بالا، بین «مرور امروز» و «همه‌ی کارت‌ها» جابه‌جا شو.",
            "برای رفتن بین کارت‌های امروز، به چپ یا راست سواپ کن؛ پیل روی کارت نشان می‌دهد روی چندمین کارت از چند کارت هستی.",
            "با زدن روی کارت، سه‌بعدی می‌چرخد و معنی را نشان می‌دهد و دکمه‌های جواب فقط بعد از برگشتن کارت ظاهر می‌شوند. معنی‌های بلند، داخل خودِ کارت اسکرول می‌شوند.",
            "نقطه‌های روی کارت، سطح جعبه را بدون هیچ متن اضافه‌ای نشان می‌دهند.",
            "دکمه‌ی دانلود، همه‌ی کارت‌ها را به‌شکل فایل سازگار با Anki بیرون می‌دهد."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Settings,
        title = if (isEn) "Settings menu" else "منوی تنظیمات بالای صفحه",
        summary = if (isEn) "Every entry in the top-right menu" else "همه‌ی گزینه‌های منوی بالا",
        points = if (isEn) listOf(
            "The three chips at the top switch between light, dark and system theme instantly.",
            "App language switches the whole interface between Persian and English. Persian is laid out right-to-left so mixed Persian/English lines stay in the right order.",
            "Word limit protects the reader from gigantic files; zero means no limit.",
            "File manager lists everything the app saved (exports, subtitles, backups) and can export or delete each one.",
            "Theme section holds the full theme picker and the accent and subtitle colours.",
            "Tutorial & AI learning is the prompt studio: learning level, dictionary-vs-JSON switch, six prompt modes, chunk size, a worked example and the copy button.",
            "App guide is this window.",
            "About shows the version and the support links."
        ) else listOf(
            "سه چیپ بالای منو، بی‌درنگ بین پوسته‌ی روشن، تاریک و پیرویِ سیستم جابه‌جا می‌شوند.",
            "زبان برنامه، کل رابط را بین فارسی و انگلیسی عوض می‌کند. حالت فارسی راست‌چین است تا خط‌هایی که فارسی و انگلیسی قاطی دارند، ترتیبشان درست بماند.",
            "حداکثر تعداد کلمه، متن‌خوان را از فایل‌های غول‌آسا حفظ می‌کند؛ عدد صفر یعنی بی‌نهایت.",
            "مدیریت فایل، هر چیزی که برنامه ذخیره کرده (خروجی‌ها، زیرنویس‌ها، پشتیبان‌ها) را فهرست می‌کند و می‌توانی هر کدام را بیرون بدهی یا پاک کنی.",
            "بخش پوسته، انتخاب‌گر کامل تم و رنگ‌های تأکیدی و رنگ زیرنویس‌ها را دارد.",
            "بخش آموزش و یادگیری AI همان پرامپت‌ساز است: سطح یادگیری، کلید دیکشنری یا JSON، شش حالت پرامپت، اندازه‌ی هر بسته، یک نمونه‌ی حل‌شده و دکمه‌ی کپی.",
            "راهنمای برنامه، همین پنجره است.",
            "درباره، نسخه‌ی برنامه و راه‌های حمایت را نشان می‌دهد."
        )
    ),
    GuideTopic(
        icon = Icons.Filled.Info,
        title = if (isEn) "Tips and troubleshooting" else "نکته‌ها و رفع اشکال",
        summary = if (isEn) "When something does not look right" else "وقتی چیزی درست کار نمی‌کند",
        points = if (isEn) listOf(
            "No dictionary results? A dictionary file has to be imported first; the popup only reads what is on the device.",
            "Subtitle drifting? Shift it in the sync section instead of re-exporting the file. Small steps of a tenth of a second are enough for most files.",
            "An AI request that fails with an authentication or model error is not retried on purpose, so you are not charged for a broken key. Fix the field and send again.",
            "If translation looks slow, raise the batch size and keep the cache on; already translated lines are reused for free.",
            "If a JSON import is rejected, paste it into the paste dialog: it says which part of the structure is wrong.",
            "The player controls fade away on their own. One tap brings them back, and the tool cluster folds itself when the controls hide.",
            "Nothing is uploaded anywhere except your own AI endpoint; dictionaries, cards and memory all live on the device."
        ) else listOf(
            "دیکشنری نتیجه نمی‌دهد؟ اول باید یک فایل دیکشنری وارد شود؛ پاپ‌آپ فقط چیزی را می‌خواند که روی دستگاه هست.",
            "زیرنویس جلو و عقب می‌رود؟ به‌جای ساختن فایل تازه، از بخش هم‌زمان‌سازی جابه‌جایش کن. برای بیشتر فایل‌ها پله‌های یک‌دهم ثانیه کافی است.",
            "درخواست هوش مصنوعی که با خطای کلید یا مدل رد شود، عمداً دوباره فرستاده نمی‌شود تا هزینه‌ی الکی ندهی. فیلد را درست کن و باز بفرست.",
            "اگر ترجمه کند است، تعداد خط هر بسته را بالا ببر و حافظه‌ی موقت را روشن نگه دار؛ جمله‌های ترجمه‌شده مجانی دوباره استفاده می‌شوند.",
            "اگر فایل JSON پذیرفته نشد، آن را در پنجره‌ی جای‌گذاری بریز؛ همان‌جا می‌گوید کدام قسمت ساختار ایراد دارد.",
            "کنترل‌های پخش‌کننده خودشان محو می‌شوند. یک ضربه برمی‌گرداندشان و مجموعه‌ی ابزار هم وقتی کنترل‌ها پنهان شوند، خودش جمع می‌شود.",
            "هیچ چیزی جایی بارگذاری نمی‌شود مگر روی سرویس هوش مصنوعی خودت؛ دیکشنری‌ها، کارت‌ها و حافظه همه روی دستگاه می‌مانند."
        )
    )
)
