package com.shejera.importing.cv.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BirthPlaceCleanerTest {
    @Test
    fun parsesPlaceWithNoSpaceAfterColon() {
        assertEquals("İSTANBUL", BirthPlaceCleaner.clean("Doğum Yeri:İSTANBUL"))
        assertEquals("ANKARA", BirthPlaceCleaner.clean("Doğum Yeri:ANKARA"))
    }

    @Test
    fun allowsMultiWordPlace() {
        assertEquals("GAZİANTEP ŞEHİR", BirthPlaceCleaner.clean("Doğum Yeri:GAZİANTEP ŞEHİR"))
    }

    @Test
    fun missingPlaceIsNull() {
        assertNull(BirthPlaceCleaner.clean("Doğum Yeri:-"))
        assertNull(BirthPlaceCleaner.clean("Doğum Yeri:"))
    }

    @Test
    fun stripsTruncatedLabel() {
        assertEquals("EBUGEN", BirthPlaceCleaner.clean("Doğum Yer:EBUGEN"))
        assertEquals("EBUGEN", BirthPlaceCleaner.clean("Doğum Yer.EBUGEN"))
    }
}
