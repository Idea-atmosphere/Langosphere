package com.example.logic

import com.example.model.BookSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zero-drop import: every object in `subtitles[]` must either become a
 * sentence or be named in [BookJsonIngest.IngestResult.droppedPositions].
 *
 * The regression is the reported 41-sentence chunk whose entry 27 — a merged
 * reviewer credit + title-page OCR + preface blurb with a 300+ character
 * `english` and a long IPA line — ended up missing even from the full sentence
 * list. Length, merged clauses and duplicate ids are never a reason to lose an
 * entry; only an object with no usable field at all is skipped, and its
 * position is recorded.
 */
class BookImportZeroDropTest {

    private val megaEnglish = "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook " +
        "THIRD EDITION TH E A R T O F ELECTR O N ICS H O RO W ITZ H " +
        "The Art of Electronics Third Edition At long last, here is the thoroughly revised and updated, " +
        "and long-anticipated, third edition of the hugely successful The Art of Electronics."

    private val longIpa = "/ðə ɑːt əv ɪˌlɛkˈtrɒnɪks θɜːd ɪˈdɪʃən æt lɒŋ lɑːst hɪər ɪz ðə ˈθʌrəli " +
        "rɪˈvaɪzd ænd ʌpˈdeɪtɪd ænd lɒŋ ænˈtɪsɪpeɪtɪd θɜːd ɪˈdɪʃən əv ðə ˈhjuːdʒli səkˈsɛsfəl/ " +
        "with extra phonetic annotation trailing on purpose to pass three hundred characters total length"

    /** The reported shape: 41 entries, p.1 → 1..27, p.3 → 28..40, p.5 → 41. */
    private fun chunk41(): String {
        val entries = (1..41).joinToString(",\n") { id ->
            val page = when (id) {
                in 1..27 -> 1
                in 28..40 -> 3
                else -> 5
            }
            val ordinal = when (id) {
                in 1..27 -> id
                in 28..40 -> id - 27
                else -> 1
            }
            val english = if (id == 27) megaEnglish else "Sample sentence number $id for the import test."
            val ipa = if (id == 27) longIpa else "/ˈsæmpəl $id/"
            """{"id":$id,"page":$page,"sentenceInPage":$ordinal,"location":"p. $page s. $ordinal","english":${quoted(english)},"translation":"ترجمهٔ $id","pronunciation":${quoted(ipa)},"words":[{"word":"sample","translation":"نمونه"}]}"""
        }
        return """{"metadata":{"source":"book"},"subtitles":[$entries]}"""
    }

    private fun quoted(text: String): String = "\"${text.replace("\"", "\\\"")}\""

    @Test
    fun `all 41 entries survive, including the merged mega entry`() {
        val result = BookJsonIngest.ingest(chunk41())
        assertTrue(result.isSuccess)
        assertEquals(41, result.receivedEntries)
        assertEquals(41, result.sentences.size)
        assertTrue(result.droppedPositions.isEmpty())

        val entry27 = result.sentences.first { it.id == 27 }
        assertTrue(entry27.english.startsWith("-- Walt Jung"))
        assertTrue(entry27.english.contains("At long last"))
        assertEquals("ترجمهٔ 27", entry27.translation)
        assertEquals(1, entry27.words.size)
        // No length cap anywhere in the schema: the long IPA arrives intact.
        assertEquals(longIpa, entry27.pronunciation)
    }

    @Test
    fun `an entry with only notes or words is kept`() {
        val json = """{"subtitles":[
            {"id":1,"english":"Hello.","translation":"سلام."},
            {"id":2,"notes":"just a note"},
            {"id":3,"words":[{"word":"hello","translation":"سلام"}]}
        ]}"""
        val result = BookJsonIngest.ingest(json)
        assertTrue(result.isSuccess)
        assertEquals(3, result.receivedEntries)
        assertEquals(3, result.sentences.size)
        assertTrue(result.droppedPositions.isEmpty())
    }

    @Test
    fun `a fully empty object is the only drop, and its position is named`() {
        val json = """{"subtitles":[
            {"id":1,"english":"Hello.","translation":"سلام."},
            {},
            {"id":3,"english":"Bye.","translation":"خداحافظ."}
        ]}"""
        val result = BookJsonIngest.ingest(json)
        assertTrue(result.isSuccess)
        assertEquals(3, result.receivedEntries)
        assertEquals(listOf(2), result.droppedPositions)
        assertEquals(listOf(1, 3), result.sentences.map { it.id })
        assertTrue(result.repairs.any { it.contains("موقعیت در JSON") })
    }

    @Test
    fun `duplicate ids with different text are both kept, the second remapped`() {
        val existing = listOf(
            BookSentence(id = 26, start = 25, end = 26, location = "p. 1 s. 26", english = "I tip my hat.", translation = "کلاه از سر برمی‌دارم."),
        )
        val incoming = listOf(
            BookSentence(id = 26, start = 25, end = 26, location = "p. 1 s. 26", english = "A completely different line.", translation = "خطی کاملاً متفاوت."),
            BookSentence(id = 27, start = 26, end = 27, location = "p. 1 s. 27", english = megaEnglish, translation = "ترجمهٔ ۲۷"),
        )
        val detailed = BookJsonIngest.mergeDetailed(existing, incoming)
        assertEquals(3, detailed.sentences.size)
        assertEquals(1, detailed.remappedIds.size)
        assertEquals(26, detailed.remappedIds.first().requestedId)
        // The remapped entry keeps its text under the fresh id.
        val moved = detailed.sentences.first { it.id == detailed.remappedIds.first().assignedId }
        assertEquals("A completely different line.", moved.english)
        // The fresh id starts past every incoming id, so the later entry that
        // legitimately owns id 27 is untouched by the re-numbering.
        assertEquals(megaEnglish, detailed.sentences.first { it.id == 27 }.english)
        // Ids stay unique, so the database primary key cannot collapse them.
        assertEquals(3, detailed.sentences.map { it.id }.toSet().size)
    }

    @Test
    fun `re-importing the same chunk still collapses into the richer entry`() {
        val first = BookJsonIngest.ingest(chunk41()).sentences
        val detailed = BookJsonIngest.mergeDetailed(first, first)
        assertEquals(41, detailed.sentences.size)
        assertTrue(detailed.remappedIds.isEmpty())
    }

    /**
     * The reported entry 27, transcribed field by field from the bug report:
     * 318-char english starting with `--`, Persian translation with a tatweel
     * (`ــ`) and guillemets, 433-char IPA line, hard/B2, and the title-page
     * note. It must import as a STANDALONE row under its own id 27 - never
     * dropped, never merged, never re-numbered.
     */
    private val reportedEntry27 = """
        {
          "id": 27,
          "start": 26,
          "end": 27,
          "location": "p. 1 s. 27",
          "page": 1,
          "sentenceInPage": 27,
          "english": "-- Walt Jung, Former IC apps engineer, and author of IC Op Amp Cookbook THIRD EDITION TH E A R T O F ELECTR O N ICS H O RO W ITZ H The Art of Electronics Third Edition At long last, here is the thoroughly revised and updated, and long-anticipated, third edition of the hugely successful The Art of Electronics.",
          "translation": "ــ والت جانگ، مهندس سابق کاربردهای مدارهای مجتمع و نویسنده کتاب آشپزی آپ‌امپ مدارهای مجتمع. ویرایش سوم، هنر الکترونیک، هوروویتز و هیل. سرانجام پس از مدت‌ها انتظار، ویرایش سوم کتاب بسیار موفق «هنر الکترونیک» که کاملاً بازبینی و به‌روزرسانی شده و مدت‌ها چشم‌انتظار آن بودیم، فرا رسید.",
          "level": "B2",
          "difficulty": "hard",
          "pronunciation": "/wɔːlt dʒʌŋ ˈfɔːrmər aɪ siː æps ˈɛndʒɪnɪər ænd ˈɔːθər əv aɪ siː ɒp æmp ˈkʊkbʊk θɜːrd ɪˈdɪʃən ðə ɑːrt əv ɪˌlɛkˈtrɒnɪks ˈhɔːrəwɪts hɪl ðə ɑːrt əv ɪˌlɛkˈtrɒnɪks θɜːrd ɪˈdɪʃən æt lɒŋ lɑːst hɪər ɪz ðə ˈθʌrəli rɪˈvaɪzd ænd ʌpˈdeɪtɪd ænd lɒŋ ænˈtɪsɪpeɪtɪd θɜːrd ɪˈdɪʃən əv ðə ˈhjuːdʒli səkˈsɛsfəl ðə ɑːrt əv ɪˌlɛkˈtrɒnɪks/",
          "notes": "این خط شامل متن امضا و بلافاصله عنوان و مقدمه ناشر در صفحه عنوان است."
        }
    """.trimIndent()

    /** The same 41-shape as [chunk41], but position 27 is the reported entry verbatim. */
    private fun chunk41Reported(): String {
        val entries = (1..41).joinToString(",\n") { id ->
            if (id == 27) return@joinToString reportedEntry27
            val page = when (id) {
                in 1..27 -> 1
                in 28..40 -> 3
                else -> 5
            }
            val ordinal = when (id) {
                in 1..27 -> id
                in 28..40 -> id - 27
                else -> 1
            }
            """{"id":$id,"page":$page,"sentenceInPage":$ordinal,"location":"p. $page s. $ordinal","english":"Sample sentence number $id for the import test.","translation":"ترجمهٔ $id"}"""
        }
        return """{"metadata":{"source":"book"},"subtitles":[$entries]}"""
    }

    @Test
    fun `the reported entry 27 imports standalone with every field intact`() {
        val result = BookJsonIngest.ingest(chunk41Reported())
        assertTrue(result.isSuccess)
        assertTrue(result.isLossless)
        assertEquals(41, result.receivedEntries)
        assertEquals(41, result.sentences.size)
        assertTrue(result.droppedPositions.isEmpty())
        assertTrue(result.itemErrors.isEmpty())
        // Strict parity: kept + dropped + failed accounts for every object.
        assertEquals(
            result.receivedEntries,
            result.sentences.size + result.droppedPositions.size + result.itemErrors.size,
        )

        val entry27 = result.sentences.first { it.id == 27 }
        assertEquals(26, entry27.start)
        assertEquals(27, entry27.end)
        assertEquals("p. 1 s. 27", entry27.location)
        assertEquals("B2", entry27.level)
        assertEquals("hard", entry27.difficulty)
        // Long-field anchors: length windows plus content that only the full
        // strings carry (leading dashes, tatweel, IPA clusters).
        assertTrue(entry27.english.length in 310..325)
        assertTrue(entry27.english.startsWith("-- Walt Jung"))
        assertTrue(entry27.english.contains("TH E A R T O F ELECTR O N ICS"))
        assertTrue(entry27.english.contains("At long last"))
        assertTrue(entry27.translation?.contains("ــ والت جانگ") == true)
        assertTrue(entry27.translation?.contains("«هنر الکترونیک»") == true)
        assertTrue((entry27.pronunciation?.length ?: 0) in 310..320)
        assertTrue(entry27.pronunciation?.startsWith("/wɔːlt") == true)
        assertTrue(entry27.pronunciation?.contains("ɪˌlɛkˈtrɒnɪks") == true)
        assertTrue(entry27.notes?.contains("امضا") == true)

        // Standalone means standalone: merging the batch into an empty
        // session keeps all 41 rows and moves no id anywhere.
        val detailed = BookJsonIngest.mergeDetailed(emptyList(), result.sentences)
        assertEquals(41, detailed.sentences.size)
        assertTrue(detailed.remappedIds.isEmpty())
        assertEquals(
            entry27.english,
            detailed.sentences.first { it.id == 27 }.english,
        )
    }

    @Test
    fun `an unescaped quote followed by Persian punctuation cannot cascade`() {
        // The inner quote ends with a Persian comma - the exact follower the
        // old letter-or-digit gate missed, which ended the string early and
        // shifted every field after it.
        val json = """{"subtitles":[
            {"id":1,"english":"First.","translation":"اول.","level":"A1"},
            {"id":2,"english":"He said "stop"، loudly.","translation":"او گفت "بایست"، بلند.","level":"A2"},
            {"id":3,"english":"Third.","translation":"سوم.","level":"A1"}
        ]}"""
        val result = BookJsonIngest.ingest(json)
        assertTrue(result.isSuccess)
        assertEquals(3, result.sentences.size)
        assertTrue(result.isLossless)
        val middle = result.sentences.first { it.id == 2 }
        assertEquals("He said \"stop\"، loudly.", middle.english)
        assertEquals("او گفت \"بایست\"، بلند.", middle.translation)
        assertEquals("A2", middle.level)
        // The neighbours on both sides are byte-identical, not shifted.
        assertEquals("First.", result.sentences.first { it.id == 1 }.english)
        assertEquals("Third.", result.sentences.first { it.id == 3 }.english)
    }

    @Test
    fun `one unparseable object costs only itself, and the rest keep full fields`() {
        // `broken` is a bare literal, so the whole-document parse throws and
        // object salvage takes over. Entries 1 and 3 must come back with
        // location and level intact - which is what proves the object path
        // ran, because the regex fallback drops both.
        val json = """{"subtitles":[
            {"id":1,"english":"First.","translation":"اول.","location":"p. 1 s. 1","level":"A1"},
            {"id":broken,"english":"Second.","translation":"دوم."},
            {"id":3,"english":"Third.","translation":"سوم.","location":"p. 1 s. 3","level":"A1"}
        ]}"""
        val logged = mutableListOf<String>()
        BookImportLog.handler = { tag, message, _ -> logged += "$tag: $message" }
        try {
            val result = BookJsonIngest.ingest(json)
            assertTrue(result.isSuccess)
            assertEquals(listOf(1, 3), result.sentences.map { it.id })
            assertEquals("p. 1 s. 1", result.sentences.first { it.id == 1 }.location)
            assertEquals("A1", result.sentences.first { it.id == 1 }.level)
            assertEquals("p. 1 s. 3", result.sentences.first { it.id == 3 }.location)
            assertTrue(result.repairs.any { it.contains("شی‌ءبه‌شی‌ء") })
            assertTrue(result.truncated)
            // The skipped span is logged, never silent.
            assertTrue(logged.any { it.startsWith("Import:") })
        } finally {
            BookImportLog.handler = null
        }
    }

    @Test
    fun `salvage names spans that carry no usable fields`() {
        val json = """{"subtitles":[
            {"id":1,"english":"First.","translation":"اول."},
            {"id":broken,"english":"Second."},
            {"id":9}
        ]}"""
        val logged = mutableListOf<String>()
        BookImportLog.handler = { tag, message, _ -> logged += "$tag: $message" }
        try {
            val result = BookJsonIngest.ingest(json)
            assertEquals(listOf(1), result.sentences.map { it.id })
            assertTrue(logged.any { it.contains("would not parse") })
            assertTrue(logged.any { it.contains("no usable fields") })
        } finally {
            BookImportLog.handler = null
        }
    }

    @Test
    fun `parity holds on a mixed batch and lossless reflects it`() {
        val json = """{"subtitles":[
            {"id":1,"english":"Hello.","translation":"سلام."},
            {},
            {"id":3,"english":"Bye.","translation":"خداحافظ."}
        ]}"""
        val result = BookJsonIngest.ingest(json)
        assertEquals(3, result.receivedEntries)
        assertEquals(
            result.receivedEntries,
            result.sentences.size + result.droppedPositions.size + result.itemErrors.size,
        )
        assertEquals(listOf(2), result.droppedPositions)
        // One empty object means the import is honest, not lossless.
        assertTrue(!result.isLossless)
        assertTrue(BookJsonIngest.ingest(chunk41()).isLossless)
    }

    @Test
    fun `long entries survive an export and re-import round trip`() {
        val first = BookJsonIngest.ingest(chunk41())
        val exported = BookJsonIngest.exportMasterJson(
            first.sentences,
            BookJsonIngest.IngestMetadata(bookTitle = "t"),
        )
        val second = BookJsonIngest.ingest(exported)
        assertTrue(second.isSuccess)
        assertEquals(41, second.sentences.size)
        assertEquals(megaEnglish, second.sentences.first { it.id == 27 }.english)
        assertEquals(longIpa, second.sentences.first { it.id == 27 }.pronunciation)
    }
}
