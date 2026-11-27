package com.sawt.voice.parser

/** تطبيع النص العربي: إزالة التشكيل وتوحيد الألف والياء والتاء المربوطة وتحويل الأرقام الهندية. */
object ArabicNormalizer {
    private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val punctuation = Regex("[\\p{P}\\p{S}]")
    private val spaces = Regex("\\s+")

    fun normalize(input: String): String {
        var s = input.trim().lowercase()
        s = diacritics.replace(s, "")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        s = toWesternDigits(s)
        s = punctuation.replace(s, " ")
        return spaces.replace(s, " ").trim()
    }

    fun tokens(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotEmpty() }

    fun toWesternDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
