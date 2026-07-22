package com.shejera.importing.cv.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelationshipLineCleanerTest {
    @Test
    fun keepsFirstRelationshipLineOnly() {
        assertEquals(
            "Annesinin Babası",
            RelationshipLineCleaner.clean("Annesinin Babası\nve Annesi"),
        )
    }

    @Test
    fun dropsDateLinesFromBand() {
        assertEquals(
            "Babası",
            RelationshipLineCleaner.clean("Babası\nDT 1.1.1900 ÖT -"),
        )
        assertNull(RelationshipLineCleaner.clean("DT 6.4.1960 ÖT:-"))
    }
}
