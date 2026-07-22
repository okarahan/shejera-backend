package com.shejera.importing.cv.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NameLineCleanerTest {
    @Test
    fun dropsDashTokensAndKeepsNames() {
        assertEquals("MEMDUH KARAHAN", NameLineCleaner.clean("MEMDUH KARAHAN -"))
        assertEquals("AYŞE", NameLineCleaner.clean("AYŞE -"))
        assertEquals("ALI VELI YILMAZ", NameLineCleaner.clean("ALI VELI YILMAZ"))
        assertNull(NameLineCleaner.clean("-"))
        assertNull(NameLineCleaner.clean(""))
    }

    @Test
    fun usesFirstLineOnly() {
        assertEquals("MEMDUH KARAHAN", NameLineCleaner.clean("MEMDUH KARAHAN -\nBabası\nDT 1.1.1900"))
    }
}
