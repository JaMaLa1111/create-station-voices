package de.jamala.station_voices

import java.lang.Character.UnicodeScript
import java.text.Normalizer
import java.util.regex.Pattern

data class VoiceModelInfo(
    val phonemeType: String = "espeak",
    val phonemeIdMap: Set<String> = emptySet(),
    val espeakVoice: String? = null,
    val sampleRate: Int = 22050
)

object TextSanitizer {

    const val DEFAULT_MAX_LENGTH = 500
    const val MAX_LABEL_LENGTH = 64

    // Minecraft formatting patterns:
    // Standard color codes §[0-9a-fk-orA-FK-OR]
    // Hex colors §x§r§r§g§g§b§b
    private val MINECRAFT_HEX_COLOR_PATTERN = Pattern.compile("(?i)§x(§[0-9a-f]){6}")
    private val MINECRAFT_COLOR_PATTERN = Pattern.compile("(?i)§[0-9a-fk-or]")
    private val AMPERSAND_COLOR_PATTERN = Pattern.compile("(?i)&(?=[0-9a-fk-or])")
    private val ALL_SECTION_SIGNS = Pattern.compile("§+")

    // Control characters and unprintable characters:
    // Remove null bytes, C0 controls (except tab/newline handled separately), DEL, and C1 controls
    private val CONTROL_CHARS_PATTERN = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F]")

    // Unicode Private Use Area (BMP: E000-F8FF, SMP: F0000-FFFFD, 100000-10FFFD)
    // Frequently used in Minecraft resource packs for custom font glyphs/icons
    private val PRIVATE_USE_AREA_PATTERN = Pattern.compile("[\\uE000-\\uF8FF]")

    // Formatting & invisible characters (zero-width spaces, joiners, BOM, bidi marks)
    private val INVISIBLE_FORMAT_CHARS = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF\\u00AD]")

    // Pictographs, dingbats, miscellaneous symbols, box drawing, block elements, shapes
    private val SYMBOLS_AND_DINGBATS = Pattern.compile(
        "[\\u2500-\\u257F" + // Box Drawing
        "\\u2580-\\u259F" + // Block Elements
        "\\u25A0-\\u25FF" + // Geometric Shapes
        "\\u2600-\\u26FF" + // Miscellaneous Symbols
        "\\u2700-\\u27BF" + // Dingbats
        "\\u2B00-\\u2BFF" + // Misc Symbols and Arrows
        "\\u2300-\\u23FF" + // Misc Technical
        "\\u2190-\\u21FF" + // Arrows
        "]"
    )

    // Espeak injection protection and model token cleanup:
    // In espeak, [[...]] enters phonetic mode which can crash espeak-ng if malformed
    // Piper special tokens: ^ (BOS), $ (EOS), _ (PAD)
    private val ESPEAK_PHONETIC_BRACKETS = Pattern.compile("[\\[\\]]")
    private val PIPER_SPECIAL_TOKENS = Pattern.compile("[\\^_]")
    private val DANGEROUS_SYMBOLS = Pattern.compile("[\\\\|~`<>]")

    // Excessive repeated punctuation patterns
    private val REPEATED_EXCLAMATION = Pattern.compile("!{2,}")
    private val REPEATED_QUESTION = Pattern.compile("\\?{2,}")
    private val REPEATED_PERIOD = Pattern.compile("\\.{4,}")
    private val REPEATED_COMMA = Pattern.compile(",{2,}")
    private val REPEATED_HYPHEN = Pattern.compile("-{2,}")
    private val REPEATED_COLON = Pattern.compile(":{2,}")
    private val REPEATED_SEMICOLON = Pattern.compile(";{2,}")
    private val MULTIPLE_SPACES = Pattern.compile(" {2,}")
    private val SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([,.:;!?)\\]])")
    private val SPACE_AFTER_OPEN_PAREN = Pattern.compile("([(])\\s+")

    // Known template placeholder tokens to preserve during template sanitization
    private val PLACEHOLDER_TOKENS = listOf("{train}", "{station}", "{name}")

    /**
     * Determines allowed UnicodeScript values for a given model language.
     * Common characters (digits, punctuation, standard spaces) and inherited marks are always included.
     */
    fun getAllowedScriptsForLanguage(language: String): Set<UnicodeScript> {
        val lang = language.split("_", "-")[0].lowercase()
        return when (lang) {
            "ar", "fa", "ur" -> setOf(
                UnicodeScript.ARABIC,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ru", "uk", "be", "bg", "mk", "kk", "sr" -> setOf(
                UnicodeScript.CYRILLIC,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "el" -> setOf(
                UnicodeScript.GREEK,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "he", "iw" -> setOf(
                UnicodeScript.HEBREW,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "zh" -> setOf(
                UnicodeScript.HAN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ja" -> setOf(
                UnicodeScript.HIRAGANA,
                UnicodeScript.KATAKANA,
                UnicodeScript.HAN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ko" -> setOf(
                UnicodeScript.HANGUL,
                UnicodeScript.HAN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "hi", "mr", "ne" -> setOf(
                UnicodeScript.DEVANAGARI,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "bn" -> setOf(
                UnicodeScript.BENGALI,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ta" -> setOf(
                UnicodeScript.TAMIL,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "te" -> setOf(
                UnicodeScript.TELUGU,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "kn" -> setOf(
                UnicodeScript.KANNADA,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ml" -> setOf(
                UnicodeScript.MALAYALAM,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "th" -> setOf(
                UnicodeScript.THAI,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "ka" -> setOf(
                UnicodeScript.GEORGIAN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            "hy" -> setOf(
                UnicodeScript.ARMENIAN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.LATIN
            )
            // Default for Latin script languages (German, English, French, Spanish, Italian, Dutch, etc.):
            else -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED
            )
        }
    }

    /**
     * Strips Minecraft formatting and color codes.
     */
    fun stripMinecraftFormatting(text: String): String {
        var result = text
        result = MINECRAFT_HEX_COLOR_PATTERN.matcher(result).replaceAll("")
        result = MINECRAFT_COLOR_PATTERN.matcher(result).replaceAll("")
        result = AMPERSAND_COLOR_PATTERN.matcher(result).replaceAll("")
        result = ALL_SECTION_SIGNS.matcher(result).replaceAll("")
        return result
    }

    /**
     * Normalizes line breaks and tabs into natural speech pauses and cleans unprintable characters.
     */
    fun cleanControlAndFormatChars(text: String): String {
        // Convert CRLF / LF / CR into sentence pauses or spaces
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                    i++
                }
                val lastChar = sb.trimEnd().lastOrNull()
                if (lastChar != null && !isSentenceEndingPunctuation(lastChar)) {
                    sb.append(". ")
                } else {
                    sb.append(" ")
                }
            } else if (c == '\t') {
                sb.append(" ")
            } else {
                sb.append(c)
            }
            i++
        }

        var result = sb.toString()
        result = CONTROL_CHARS_PATTERN.matcher(result).replaceAll("")
        result = PRIVATE_USE_AREA_PATTERN.matcher(result).replaceAll("")
        result = INVISIBLE_FORMAT_CHARS.matcher(result).replaceAll("")
        return result
    }

    private fun isSentenceEndingPunctuation(c: Char): Boolean {
        return c == '.' || c == '!' || c == '?' || c == ':' || c == ';' || c == '؟' || c == '。' || c == '！' || c == '？'
    }

    /**
     * Strips emojis and pictographic symbols (both SMP surrogate pairs and BMP symbols).
     */
    fun stripEmojisAndPictographs(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Check if codePoint is in emoji / pictograph ranges in SMP (1F000 - 1FAFF)
            // or Supplemental Symbols and Pictographs, Emoticons, Transport, etc.
            val isSmpEmoji = codePoint in 0x1F000..0x1FAFF ||
                    codePoint in 0x1F300..0x1F5FF || // Misc Symbols and Pictographs
                    codePoint in 0x1F600..0x1F64F || // Emoticons
                    codePoint in 0x1F680..0x1F6FF || // Transport and Map
                    codePoint in 0x1F900..0x1F9FF || // Supplemental Symbols
                    codePoint in 0x2600..0x27BF      // Misc symbols and dingbats

            if (!isSmpEmoji && !Character.isSurrogate(text[i])) {
                sb.appendCodePoint(codePoint)
            } else if (charCount > 1 && !isSmpEmoji) {
                // If surrogate pair but not an emoji, only keep if it has a valid speech script
                val script = try { UnicodeScript.of(codePoint) } catch (_: Exception) { UnicodeScript.UNKNOWN }
                if (script != UnicodeScript.UNKNOWN) {
                    sb.appendCodePoint(codePoint)
                }
            }
            i += charCount
        }

        val withoutSmp = sb.toString()
        return SYMBOLS_AND_DINGBATS.matcher(withoutSmp).replaceAll("")
    }

    /**
     * Expands common abbreviations and symbols into speakable words based on the model's language.
     */
    fun expandSymbols(text: String, language: String): String {
        val lang = language.split("_", "-")[0].lowercase()
        var result = text

        // Number signs: #1 -> number 1 / Nummer 1 / etc.
        result = when (lang) {
            "de" -> result.replace(Regex("#\\s*(\\d+)"), " Nummer $1 ")
            "fr" -> result.replace(Regex("#\\s*(\\d+)"), " numéro $1 ")
            "es" -> result.replace(Regex("#\\s*(\\d+)"), " número $1 ")
            "ar" -> result.replace(Regex("#\\s*(\\d+)"), " رقم $1 ")
            else -> result.replace(Regex("#\\s*(\\d+)"), " number $1 ")
        }
        // Remove standalone #
        result = result.replace("#", " ")

        // Ampersand: & -> and / und / et / y / e / etc.
        val andWord = when (lang) {
            "de" -> " und "
            "fr" -> " et "
            "es" -> " y "
            "it", "pt" -> " e "
            "nl" -> " en "
            "pl" -> " i "
            "ru", "uk" -> " и "
            "ar" -> " و "
            else -> " and "
        }
        result = result.replace("&", andWord)

        // Percentage: % / ٪ -> percent / Prozent / etc.
        val percentWord = when (lang) {
            "de" -> " Prozent "
            "fr" -> " pour cent "
            "es" -> " por ciento "
            "it" -> " per cento "
            "nl" -> " procent "
            "ru" -> " процентов "
            "ar" -> " بالمئة "
            else -> " percent "
        }
        result = result.replace("%", percentWord).replace("٪", percentWord)

        // At sign: @ -> at / bei / à / etc.
        val atWord = when (lang) {
            "de" -> " bei "
            "fr" -> " à "
            "es" -> " en "
            else -> " at "
        }
        result = result.replace("@", atWord)

        // Plus sign: + -> plus / etc.
        val plusWord = when (lang) {
            "es" -> " más "
            "ar" -> " زائد "
            else -> " plus "
        }
        result = result.replace("+", plusWord)

        // Currency symbols: $, €, £ (handles both prefix €5 / $10 and suffix 5€ / 10$)
        val euroWord = if (lang == "de") "Euro" else "euros"
        val dollarWord = if (lang == "de") "Dollar" else "dollars"
        val poundWord = if (lang == "de") "Pfund" else "pounds"

        result = replaceCurrency(result, "€", euroWord)
        result = replaceCurrency(result, "$", dollarWord)
        result = replaceCurrency(result, "£", poundWord)

        return result
    }

    private fun replaceCurrency(text: String, symbol: String, word: String): String {
        val pattern = Pattern.compile("""(?:(?:\Q$symbol\E\s*(\d+))|(?:\b(\d+)\s*\Q$symbol\E))""")
        val matcher = pattern.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val amount = matcher.group(1) ?: matcher.group(2) ?: ""
            matcher.appendReplacement(sb, " $amount $word ")
        }
        matcher.appendTail(sb)
        return sb.toString().replace(symbol, " ")
    }

    /**
     * Normalizes punctuation, typographic quotes, dashes, and neutralizes injection vectors.
     */
    fun normalizePunctuation(text: String, preservePlaceholders: Boolean = false): String {
        var result = text

        // Typographic quotes -> standard quotes
        result = result.replace('“', '"').replace('”', '"').replace('„', '"')
            .replace('«', '"').replace('»', '"')
            .replace('‘', '\'').replace('’', '\'').replace('‚', '\'')
            .replace('‹', '\'').replace('›', '\'')

        // Typographic dashes -> standard hyphen
        result = result.replace('–', '-').replace('—', '-').replace('―', '-')

        // Espeak phonetic brackets: [ ] -> ( )
        result = ESPEAK_PHONETIC_BRACKETS.matcher(result).replaceAll(" ")

        // Dangerous and special model tokens
        result = PIPER_SPECIAL_TOKENS.matcher(result).replaceAll(" ")
        result = DANGEROUS_SYMBOLS.matcher(result).replaceAll(" ")

        // Braces { } (unless preserving template placeholders)
        if (!preservePlaceholders) {
            result = result.replace("{", " ").replace("}", " ")
        }

        // Collapse excessive repeated punctuation
        result = REPEATED_EXCLAMATION.matcher(result).replaceAll("!")
        result = REPEATED_QUESTION.matcher(result).replaceAll("?")
        result = REPEATED_PERIOD.matcher(result).replaceAll("...")
        result = REPEATED_COMMA.matcher(result).replaceAll(",")
        result = REPEATED_HYPHEN.matcher(result).replaceAll("-")
        result = REPEATED_COLON.matcher(result).replaceAll(":")
        result = REPEATED_SEMICOLON.matcher(result).replaceAll(";")

        // Clean spaces before closing punctuation and after opening parenthesis
        result = SPACE_BEFORE_PUNCTUATION.matcher(result).replaceAll("$1")
        result = SPACE_AFTER_OPEN_PAREN.matcher(result).replaceAll("$1")

        return result
    }

    /**
     * Collapses whitespace and trims.
     */
    fun normalizeWhitespace(text: String): String {
        return MULTIPLE_SPACES.matcher(text.trim()).replaceAll(" ")
    }

    /**
     * Truncates text cleanly at a sentence or word boundary within max length.
     */
    fun truncateLength(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.length <= maxLength) return text

        val slice = text.substring(0, maxLength)
        // Try finding a sentence boundary
        val lastSentenceEnd = maxOf(slice.lastIndexOf(". "), slice.lastIndexOf("! "), slice.lastIndexOf("? "))
        if (lastSentenceEnd > maxLength / 2) {
            return slice.substring(0, lastSentenceEnd + 1).trim()
        }

        // Try finding a word boundary
        val lastSpace = slice.lastIndexOf(' ')
        if (lastSpace > maxLength / 2) {
            return slice.substring(0, lastSpace).trim()
        }

        return slice.trim()
    }

    /**
     * Sanitizes general input text for speech synthesis with language awareness.
     * This preserves Unicode letters corresponding to the model language (e.g. German umlauts, Arabic letters).
     */
    fun sanitize(text: String, language: String = "en_US", maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.isBlank()) return ""

        // 1. Unicode NFC Canonical Normalization (ensures precomposed characters like ä, ö, ü, é)
        var sanitized = Normalizer.normalize(text, Normalizer.Form.NFC)

        // 2. Strip Minecraft color codes and formatting
        sanitized = stripMinecraftFormatting(sanitized)

        // 3. Clean control and invisible characters, format newlines as natural pauses
        sanitized = cleanControlAndFormatChars(sanitized)

        // 4. Strip emojis and pictorial symbols
        sanitized = stripEmojisAndPictographs(sanitized)

        // 5. Expand symbols language-specifically (& -> und/and, % -> Prozent/percent, etc.)
        sanitized = expandSymbols(sanitized, language)

        // 6. Normalize punctuation, typographic marks, quotes, brackets
        sanitized = normalizePunctuation(sanitized, preservePlaceholders = false)

        // 7. Normalize whitespace
        sanitized = normalizeWhitespace(sanitized)

        // 8. Truncate to maximum length
        sanitized = truncateLength(sanitized, maxLength)

        return sanitized
    }

    /**
     * Sanitizes template profile text, preserving recognized placeholders ({train}, {station}, {name}).
     */
    fun sanitizeTemplate(text: String, language: String = "en_US", maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.isBlank()) return ""

        // Temporarily protect known placeholders using alphanumeric tokens that won't be stripped
        var protectedText = text
        val placeholderMap = mutableMapOf<String, String>()
        for ((idx, ph) in PLACEHOLDER_TOKENS.withIndex()) {
            val token = "XPLACEHOLDER${idx}X"
            if (protectedText.contains(ph, ignoreCase = true)) {
                protectedText = protectedText.replace(Regex(Regex.escape(ph), RegexOption.IGNORE_CASE), token)
                placeholderMap[token] = ph
            }
        }

        var sanitized = sanitize(protectedText, language, maxLength)

        // Restore known placeholders
        for ((token, original) in placeholderMap) {
            sanitized = sanitized.replace(token, original)
        }

        return sanitized
    }

    /**
     * Sanitizes a train or station name label.
     */
    fun sanitizeLabel(label: String, maxLength: Int = MAX_LABEL_LENGTH): String {
        if (label.isBlank()) return ""
        var cleaned = stripMinecraftFormatting(label)
        cleaned = cleanControlAndFormatChars(cleaned)
        cleaned = stripEmojisAndPictographs(cleaned)
        cleaned = ESPEAK_PHONETIC_BRACKETS.matcher(cleaned).replaceAll(" ")
        cleaned = PIPER_SPECIAL_TOKENS.matcher(cleaned).replaceAll(" ")
        cleaned = DANGEROUS_SYMBOLS.matcher(cleaned).replaceAll(" ")
        cleaned = normalizeWhitespace(cleaned)
        return truncateLength(cleaned, maxLength)
    }

    /**
     * Guardrail: filters text strictly against the model's configuration and allowed language scripts.
     * Prevents characters from unsupported scripts/alphabets from being passed into the model.
     * For character-based models ("text"), strictly validates against the model's phoneme_id_map.
     */
    fun filterForModel(text: String, voiceModel: VoiceModelInfo?, language: String): String {
        if (text.isBlank()) return ""

        // Case 1: Character-based model ("text")
        if (voiceModel?.phonemeType.equals("text", ignoreCase = true) && voiceModel?.phonemeIdMap?.isNotEmpty() == true) {
            val allowedChars = voiceModel.phonemeIdMap
            val sb = StringBuilder(text.length)
            for (ch in text) {
                val str = ch.toString()
                if (allowedChars.contains(str) || allowedChars.contains(str.lowercase()) || ch == ' ') {
                    sb.append(ch)
                }
            }
            return normalizeWhitespace(sb.toString())
        }

        // Case 2: General / Espeak-based model:
        // Validate characters against allowed scripts for this model's language
        val allowedScripts = getAllowedScriptsForLanguage(language)
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            val isSpace = Character.isWhitespace(codePoint) || codePoint == ' '.code
            if (isSpace) {
                sb.append(' ')
                i += charCount
                continue
            }

            // Allowed standard punctuation
            val isStandardPunctuation = codePoint == '.'.code || codePoint == ','.code || codePoint == '!'.code ||
                    codePoint == '?'.code || codePoint == ':'.code || codePoint == ';'.code ||
                    codePoint == '\''.code || codePoint == '"'.code || codePoint == '-'.code ||
                    codePoint == '('.code || codePoint == ')'.code || codePoint == '/'.code

            // Allowed language-specific punctuation (e.g. Arabic, Spanish, CJK)
            val isLanguagePunctuation = codePoint == '،'.code || codePoint == '؟'.code || codePoint == '؛'.code ||
                    codePoint == 'ـ'.code || codePoint == '٪'.code || codePoint == '¿'.code ||
                    codePoint == '¡'.code || codePoint == '。'.code || codePoint == '、'.code

            if (isStandardPunctuation || isLanguagePunctuation) {
                sb.appendCodePoint(codePoint)
                i += charCount
                continue
            }

            // Allowed digits
            if (Character.isDigit(codePoint)) {
                val script = try { UnicodeScript.of(codePoint) } catch (_: Exception) { UnicodeScript.UNKNOWN }
                if (allowedScripts.contains(script) || script == UnicodeScript.COMMON) {
                    sb.appendCodePoint(codePoint)
                }
                i += charCount
                continue
            }

            // Letters and combining marks:
            // Must belong to one of the allowed Unicode scripts for the model's language!
            if (Character.isLetter(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(codePoint) == Character.COMBINING_SPACING_MARK.toInt()) {
                val script = try { UnicodeScript.of(codePoint) } catch (_: Exception) { UnicodeScript.UNKNOWN }
                if (allowedScripts.contains(script)) {
                    sb.appendCodePoint(codePoint)
                }
            }

            i += charCount
        }

        return normalizeWhitespace(sb.toString())
    }

    /**
     * Checks if text contains at least one speakable letter or digit.
     * Prevents calling the TTS model with empty strings, whitespace, or bare punctuation.
     */
    fun isSpeakable(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    /**
     * Complete pipeline: sanitizes text, applies model guardrails matching the model's language,
     * and returns the final safe string, or null if the string is empty or unspeakable.
     */
    fun sanitizeForModel(text: String, voiceModel: VoiceModelInfo?, language: String): String? {
        val sanitized = sanitize(text, language)
        val filtered = filterForModel(sanitized, voiceModel, language)
        if (!isSpeakable(filtered)) {
            return null
        }
        return filtered
    }
}
