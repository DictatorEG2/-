package com.sawt.voice.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicNormalizerTest {
    @Test fun removesDiacriticsAndUnifiesLetters() {
        assertEquals("يا صوت افتح الرسايل", ArabicNormalizer.normalize("يا صَوْت افتَح الرسائل"))
        assertEquals("لاعلي", ArabicNormalizer.normalize("لأعلى"))
    }

    @Test fun convertsArabicIndicDigits() {
        assertEquals("رقم 3", ArabicNormalizer.normalize("رقم ٣"))
        assertEquals("0100", ArabicNormalizer.normalize("۰۱۰۰"))
    }

    @Test fun stripsPunctuationAndExtraSpaces() {
        assertEquals("افتح الفيس", ArabicNormalizer.normalize("  افتح،   الفيس! "))
    }
}
