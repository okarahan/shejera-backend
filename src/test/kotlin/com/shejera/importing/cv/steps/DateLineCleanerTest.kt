package com.shejera.importing.cv.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateLineCleanerTest {
    @Test
    fun parsesSingleAndDoubleDigitDayMonth() {
        val a = DateLineCleaner.clean("DT:6.4.1960 ÖT:12.11.2020")
        assertEquals("06.04.1960", a.birthDate)
        assertEquals("12.11.2020", a.deathDate)

        val b = DateLineCleaner.clean("DT:01.12.1900 ÖT:-")
        assertEquals("01.12.1900", b.birthDate)
        assertNull(b.deathDate)
    }

    @Test
    fun toleratesSpacesInsideDate() {
        val a = DateLineCleaner.clean("DT:1.7.1892 ÖT:29 .12.1942")
        assertEquals("01.07.1892", a.birthDate)
        assertEquals("29.12.1942", a.deathDate)

        val b = DateLineCleaner.clean("DT:1.7.1901 ÖT:22 8.1954")
        assertEquals("01.07.1901", b.birthDate)
        assertEquals("22.08.1954", b.deathDate)
    }
}
