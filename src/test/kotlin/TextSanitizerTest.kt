import de.jamala.station_voices.TextSanitizer
import de.jamala.station_voices.VoiceModelInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextSanitizerTest {

    @Test
    fun testStripMinecraftFormatting() {
        val colored = "§aGreen §lBold §rReset §x§f§f§5§5§5§5Custom"
        val stripped = TextSanitizer.stripMinecraftFormatting(colored)
        assertEquals("Green Bold Reset Custom", TextSanitizer.normalizeWhitespace(stripped))

        val stray = "§§cSection § Test"
        val strayStripped = TextSanitizer.stripMinecraftFormatting(stray)
        assertEquals("Section Test", TextSanitizer.normalizeWhitespace(strayStripped))
    }

    @Test
    fun testGermanLanguagePreservesUmlautsAndExpandsSymbols() {
        val input = "§eZug nach München (Gleis #3 & #4) pünktlich zu 100% mit 5€ Rabatt! 🚆"
        val sanitized = TextSanitizer.sanitize(input, "de_DE")

        // Umlauts must be preserved
        assertTrue(sanitized.contains("München"), "German umlauts like 'ü' must be preserved: $sanitized")

        // Minecraft codes stripped
        assertFalse(sanitized.contains("§"))

        // Emojis stripped
        assertFalse(sanitized.contains("🚆"))

        // Symbols expanded to German words
        assertTrue(sanitized.contains("Nummer 3"), "Should expand #3 to Nummer 3: $sanitized")
        assertTrue(sanitized.contains("und"), "Should expand & to und: $sanitized")
        assertTrue(sanitized.contains("Prozent"), "Should expand % to Prozent: $sanitized")
        assertTrue(sanitized.contains("Euro"), "Should expand € to Euro: $sanitized")

        // Brackets normalized
        assertFalse(sanitized.contains("["))
        assertFalse(sanitized.contains("]"))
    }

    @Test
    fun testGermanModelGuardrailStripsUnsupportedScripts() {
        val germanModel = VoiceModelInfo(phonemeType = "espeak")

        // Input with German + Arabic + Cyrillic
        val mixedInput = "Zug nach München مرحبا Привет"
        val result = TextSanitizer.sanitizeForModel(mixedInput, germanModel, "de_DE")

        assertNotNull(result)
        assertTrue(result!!.contains("München"))
        // Arabic and Cyrillic must be removed by the German model guardrail
        assertFalse(result.contains("مرحبا"), "Arabic should not be passed to German model")
        assertFalse(result.contains("Привет"), "Cyrillic should not be passed to German model")
        assertEquals("Zug nach München", result)
    }

    @Test
    fun testArabicLanguagePreservesArabicScriptAndHarakat() {
        // Arabic text with harakat (diacritics) and Arabic punctuation
        val arabicInput = "§cقِطَارٌ سَرِيعٌ§r، يَصِلُ إِلَى الْمَحَطَّةِ رقم #5 & #6 بنسبة 100% 🚆"
        val sanitized = TextSanitizer.sanitize(arabicInput, "ar_JO")

        // Arabic letters and harakat preserved
        assertTrue(sanitized.contains("قِطَارٌ"), "Arabic letters and harakat must be preserved: $sanitized")
        assertTrue(sanitized.contains("سَرِيعٌ"), "Arabic letters must be preserved: $sanitized")
        assertTrue(sanitized.contains("،"), "Arabic comma must be preserved: $sanitized")

        // Minecraft code stripped
        assertFalse(sanitized.contains("§"))

        // Emoji stripped
        assertFalse(sanitized.contains("🚆"))

        // Arabic symbol expansion
        assertTrue(sanitized.contains("رقم 5"), "Should expand #5 to رقم 5: $sanitized")
        assertTrue(sanitized.contains("و"), "Should expand & to و in Arabic: $sanitized")
        assertTrue(sanitized.contains("بالمئة"), "Should expand % to بالمئة in Arabic: $sanitized")
    }

    @Test
    fun testArabicModelGuardrailAllowsArabicAndStripsUnsupportedScripts() {
        val arabicModel = VoiceModelInfo(phonemeType = "espeak")

        val mixed = "قطار سريع Привет"
        val result = TextSanitizer.sanitizeForModel(mixed, arabicModel, "ar_JO")

        assertNotNull(result)
        assertTrue(result!!.contains("قطار سريع"))
        // Cyrillic must be stripped from Arabic model
        assertFalse(result.contains("Привет"), "Cyrillic should not be passed to Arabic model")
    }

    @Test
    fun testEnglishLanguageExpandsSymbols() {
        val input = "Train #4 on track 1 & 2 is 100% on time, tickets $10 / £8 🚂"
        val sanitized = TextSanitizer.sanitize(input, "en_US")

        assertTrue(sanitized.contains("number 4"), "Should expand #4 to number 4: $sanitized")
        assertTrue(sanitized.contains("and"), "Should expand & to and: $sanitized")
        assertTrue(sanitized.contains("percent"), "Should expand % to percent: $sanitized")
        assertTrue(sanitized.contains("dollars"), "Should expand $10 to 10 dollars: $sanitized")
        assertTrue(sanitized.contains("pounds"), "Should expand £8 to 8 pounds: $sanitized")
        assertFalse(sanitized.contains("🚂"), "Should strip train emoji: $sanitized")
    }

    @Test
    fun testTextBasedModelGuardrailStrictlyEnforcesPhonemeIdMap() {
        // A character-based model where supported characters are explicitly listed in phoneme_id_map
        val textModel = VoiceModelInfo(
            phonemeType = "text",
            phonemeIdMap = setOf("a", "b", "c", "1", "2", "!", ".", " ")
        )

        val input = "a b c x y z 1 2!"
        val result = TextSanitizer.filterForModel(input, textModel, "en_US")

        // Only characters in phonemeIdMap should remain
        assertEquals("a b c 1 2!", result)
        assertFalse(result.contains("x"))
        assertFalse(result.contains("y"))
        assertFalse(result.contains("z"))
    }

    @Test
    fun testEspeakPhoneticInjectionPrevention() {
        // In espeak, [[...]] activates phonetic mode which can crash native code if malformed
        val injection = "Normal text [[dIs Iz f@nEtIk]] more text"
        val sanitized = TextSanitizer.sanitize(injection, "en_US")

        assertFalse(sanitized.contains("["), "Square brackets must be stripped to prevent phonetic mode injection")
        assertFalse(sanitized.contains("]"), "Square brackets must be stripped to prevent phonetic mode injection")
        assertTrue(sanitized.contains("Normal text"))
        assertTrue(sanitized.contains("more text"))
    }

    @Test
    fun testControlCharactersAndPrivateUseAreaStripped() {
        // \u0000 (null), \u0007 (bell), \u001B (esc), \uE001 (Minecraft icon PUA), \u200B (ZWSP)
        val dirty = "Train\u0000 arriving\u0007 at\u001B platform \uE001\u200B 3"
        val sanitized = TextSanitizer.sanitize(dirty, "en_US")

        assertEquals("Train arriving at platform 3", sanitized)
    }

    @Test
    fun testNewlinesConvertedToSpeechPauses() {
        val multiline1 = "Next stop Central Station\nDoors will open on the left"
        val sanitized1 = TextSanitizer.sanitize(multiline1, "en_US")
        assertEquals("Next stop Central Station. Doors will open on the left", sanitized1)

        val multiline2 = "Next stop Central Station!\nDoors will open on the left"
        val sanitized2 = TextSanitizer.sanitize(multiline2, "en_US")
        assertEquals("Next stop Central Station! Doors will open on the left", sanitized2)
    }

    @Test
    fun testSanitizeTemplatePreservesPlaceholders() {
        val template = "§aTrain {train} is arriving at {station} (track {name})! 🚆"
        val sanitized = TextSanitizer.sanitizeTemplate(template, "en_US")

        assertTrue(sanitized.contains("{train}"), "Should preserve {train} placeholder: $sanitized")
        assertTrue(sanitized.contains("{station}"), "Should preserve {station} placeholder: $sanitized")
        assertTrue(sanitized.contains("{name}"), "Should preserve {name} placeholder: $sanitized")
        assertFalse(sanitized.contains("§a"))
        assertFalse(sanitized.contains("🚆"))
    }

    @Test
    fun testSanitizeLabel() {
        val rawTrainName = "§c[Express 404]§r 🚂"
        val cleanName = TextSanitizer.sanitizeLabel(rawTrainName)

        assertEquals("Express 404", cleanName)
    }

    @Test
    fun testIsSpeakableAndUnspeakableGuardrail() {
        assertTrue(TextSanitizer.isSpeakable("Hello 123"))
        assertTrue(TextSanitizer.isSpeakable("مرحبا"))
        assertTrue(TextSanitizer.isSpeakable("Zug"))

        assertFalse(TextSanitizer.isSpeakable(""))
        assertFalse(TextSanitizer.isSpeakable("   "))
        assertFalse(TextSanitizer.isSpeakable("..."))
        assertFalse(TextSanitizer.isSpeakable("?!:;"))

        // When input has only emojis, sanitizeForModel should return null
        val onlyEmojis = "🚆 🚂 🎉 ⚡"
        val result = TextSanitizer.sanitizeForModel(onlyEmojis, null, "en_US")
        assertNull(result, "Sanitizing only emojis should result in null to avoid invoking model")

        // When input has only unsupported script characters for the model
        val arabicInGerman = "مرحبا"
        val germanModel = VoiceModelInfo(phonemeType = "espeak")
        val germanResult = TextSanitizer.sanitizeForModel(arabicInGerman, germanModel, "de_DE")
        assertNull(germanResult, "Unsupported script characters should be filtered, resulting in null")
    }

    @Test
    fun testTruncationGuardrail() {
        val longText = "Sentence one. ".repeat(100)
        val truncated = TextSanitizer.truncateLength(longText, 50)
        assertTrue(truncated.length <= 50)
        assertTrue(truncated.endsWith("."))
    }

    @Test
    fun testJapaneseLanguageAllowsKanjiHiraganaKatakana() {
        val jaModel = VoiceModelInfo(phonemeType = "espeak")
        val jaText = "§a東京行き§rの電車が参ります。"
        val result = TextSanitizer.sanitizeForModel(jaText, jaModel, "ja_JP")

        assertNotNull(result)
        assertTrue(result!!.contains("東京行きの電車が参ります。"))
        assertFalse(result.contains("§a"))

        // Rejects Arabic and Cyrillic
        val mixed = "東京 Привет مرحبا"
        val mixedResult = TextSanitizer.sanitizeForModel(mixed, jaModel, "ja_JP")
        assertEquals("東京", mixedResult)
    }

    @Test
    fun testRussianLanguageAllowsCyrillicAndRejectsOtherScripts() {
        val ruModel = VoiceModelInfo(phonemeType = "espeak")
        val ruText = "Поезд прибывает на путь номер 1 مرحبا 你好"
        val result = TextSanitizer.sanitizeForModel(ruText, ruModel, "ru_RU")

        assertNotNull(result)
        assertTrue(result!!.contains("Поезд прибывает на путь номер 1"))
        assertFalse(result.contains("مرحبا"))
        assertFalse(result.contains("你好"))
    }

    @Test
    fun testSpanishLanguageAllowsAccentsAndInvertedPunctuation() {
        val esModel = VoiceModelInfo(phonemeType = "espeak")
        val esText = "¡Atención! El tren #2 llega a la vía 1 & 2 con retraso de 10%."
        val result = TextSanitizer.sanitizeForModel(esText, esModel, "es_ES")

        assertNotNull(result)
        assertTrue(result!!.contains("¡Atención!"))
        assertTrue(result.contains("número 2"))
        assertTrue(result.contains(" y "))
        assertTrue(result.contains("por ciento"))
    }

    @Test
    fun testArabicIndicDigitsPreservedInArabic() {
        val arModel = VoiceModelInfo(phonemeType = "espeak")
        val arText = "المحطة رقم ٤ على الرصيف ٢"
        val result = TextSanitizer.sanitizeForModel(arText, arModel, "ar_JO")

        assertNotNull(result)
        assertTrue(result!!.contains("٤"))
        assertTrue(result.contains("٢"))
    }
}
