package com.shejera.importing.cv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardTextParserTest {
    @Test
    fun parsesRootCardWithDtAndOtOnSameLine() {
        val parsed =
            CardTextParser.parse(
                """
                ÖMER KARAHAN
                Kendisi
                DT:29.1.1984 ÖT:-
                Doğum Yeri:BERLİN
                """.trimIndent(),
            )

        assertEquals("ÖMER", parsed.givenName)
        assertEquals("KARAHAN", parsed.surname)
        assertEquals("Kendisi", parsed.role)
        assertEquals("29.01.1984", parsed.birthDate)
        assertNull(parsed.deathDate)
        assertEquals("BERLİN", parsed.birthPlace)
    }

    @Test
    fun parsesWrappedRelationshipAndMissingSurnameDash() {
        val parsed =
            CardTextParser.parse(
                """
                HAYRİYE -
                Annesinin
                Annesi
                DT. 6.1.1932 ÖT:13.7.2019
                Doğum Yeri EBUGEN
                """.trimIndent(),
            )

        assertEquals("HAYRİYE", parsed.givenName)
        assertNull(parsed.surname)
        assertEquals("Annesinin Annesi", parsed.role)
        assertEquals("06.01.1932", parsed.birthDate)
        assertEquals("13.07.2019", parsed.deathDate)
        assertEquals("EBUGEN", parsed.birthPlace)
    }

    @Test
    fun parsesOcrNoisyDateAndPlaceLines() {
        val parsed =
            CardTextParser.parse(
                """
                MERYEM KARAHAN
                Annesi
                6.4.1960 ÖT:-
                jum Yeri:EBUGEN
                """.trimIndent(),
            )

        assertEquals("MERYEM", parsed.givenName)
        assertEquals("KARAHAN", parsed.surname)
        assertEquals("Annesi", parsed.role)
        assertEquals("06.04.1960", parsed.birthDate)
        assertNull(parsed.deathDate)
        assertEquals("EBUGEN", parsed.birthPlace)
    }

    @Test
    fun parsesGarbagePrefixBeforeDt() {
        val parsed =
            CardTextParser.parse(
                """
                ADEM KARAHAN
                Babası
                ni DT:1.5.1957 ÖT:-
                Doğum Yeri:DEDELİ
                """.trimIndent(),
            )

        assertEquals("ADEM", parsed.givenName)
        assertEquals("Babası", parsed.role)
        assertEquals("01.05.1957", parsed.birthDate)
        assertEquals("DEDELİ", parsed.birthPlace)
    }

    @Test
    fun sexStillComesOnlyFromColorBar() {
        assertEquals("F", CardTextParser.resolveSex("Babası", "F"))
        assertNull(CardTextParser.resolveSex("Annesi", null))
    }
}
