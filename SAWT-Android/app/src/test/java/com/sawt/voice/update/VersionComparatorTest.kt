package com.sawt.voice.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionComparatorTest {
    @Test fun comparesNumericPartsRatherThanLexicographicText() {
        assertEquals(1, VersionComparator.compare("v1.10.0", "1.9.9"))
        assertEquals(-1, VersionComparator.compare("v1.2.9", "1.3.0"))
        assertEquals(0, VersionComparator.compare("v0.1.0", "0.1.0"))
    }

    @Test fun rejectsNonNumericVersionTags() {
        assertNull(VersionComparator.compare("preview", "0.1.0"))
        assertNull(VersionComparator.compare("v1.0.0", "unknown"))
    }
}
