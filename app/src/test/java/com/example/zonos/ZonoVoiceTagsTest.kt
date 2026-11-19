package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The caption under a voice's name: accent / style and gender, in the UI language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZonoVoiceTagsTest {

    @Test
    fun englishVoicesShowAccentAndGender() {
        val cillian = ZonoVoice("default_2c5123a959875341", "Cillian", "English", tag = "irish", gender = "m")
        assertEquals("Irish · male", ZonoVoiceTags.caption(cillian, isFa = false))
        assertEquals("ایرلندی · مرد", ZonoVoiceTags.caption(cillian, isFa = true))

        val imogen = ZonoVoice("default_b78b28baf5c31a1d", "Imogen", "English", tag = "british", gender = "f")
        assertEquals("British · female", ZonoVoiceTags.caption(imogen, isFa = false))
        assertEquals("بریتانیایی · زن", ZonoVoiceTags.caption(imogen, isFa = true))
    }

    @Test
    fun styleVoicesShowTheStyleWord() {
        val thaddeus = ZonoVoice("x", "Thaddeus", "English", tag = "announcer", gender = "m")
        assertEquals("announcer · male", ZonoVoiceTags.caption(thaddeus, isFa = false))
        assertEquals("گوینده · مرد", ZonoVoiceTags.caption(thaddeus, isFa = true))
        val willow = ZonoVoice("y", "Willow", "English", tag = "asmr", gender = "f")
        assertEquals("ASMR · female", ZonoVoiceTags.caption(willow, isFa = false))
        assertEquals("ASMR · زن", ZonoVoiceTags.caption(willow, isFa = true))
    }

    @Test
    fun voicesWithoutAccentShowOnlyTheGender() {
        val gaspard = ZonoVoice("z", "Gaspard", "French", gender = "m")
        assertEquals("male", ZonoVoiceTags.caption(gaspard, isFa = false))
        assertEquals("مرد", ZonoVoiceTags.caption(gaspard, isFa = true))
    }

    @Test
    fun unknownTagsAreShownAsIsAndEmptyVoicesHaveNoCaption() {
        assertEquals("Scouse · female", ZonoVoiceTags.caption(ZonoVoice("a", "A", "English", tag = "Scouse", gender = "F"), isFa = false))
        assertNull(ZonoVoiceTags.caption(ZonoVoice("b", "B", "English"), isFa = false))
        assertNull(ZonoVoiceTags.genderLabel("?", isFa = false))
    }

    /** The bundled voices.json carries a gender for every voice and an accent / style for every English one. */
    @Test
    fun bundledVoicesAreLabelled() {
        val file = listOf("src/main/assets/zonos/voices.json", "app/src/main/assets/zonos/voices.json")
            .map(::File).firstOrNull { it.exists() } ?: return // not run from the module directory
        val arr = JSONArray(file.readText())
        assertEquals(27, arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val voice = ZonoVoice(
                id = o.getString("id"),
                name = o.getString("name"),
                language = o.getString("language"),
                tag = o.optString("tag", ""),
                gender = o.optString("gender", ""),
            )
            assertTrue("${voice.name}: gender", voice.gender == "m" || voice.gender == "f")
            if (voice.language == "English") {
                assertTrue("${voice.name}: tag", ZonoVoiceTags.tagLabel(voice.tag, isFa = true) != null)
            }
            assertTrue("${voice.name}: caption", ZonoVoiceTags.caption(voice, isFa = false) != null)
        }
    }
}
