package com.shejera.importing.cv

import com.shejera.importing.CvImageRecognizer
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectorLinkTest {
    @Test
    fun rootConnectsToMotherAndFatherViaArrows() {
        val imagePath = Path.of("src/test/resources/cv/fixtures/step1-source.png")
        if (!java.nio.file.Files.exists(imagePath)) return

        val tree = CvImageRecognizer().recognize(imagePath)
        val byId = tree.people.associateBy { it.tempId }
        val rootFamily =
            tree.families.firstOrNull { it.childTempIds.contains("root") }
                ?: tree.families.firstOrNull { fam ->
                    fam.childTempIds.any { byId[it]?.role?.contains("Kendi", ignoreCase = true) == true }
                }

        assertTrue(rootFamily != null, "Expected a family where Ömer/root is the child")
        val spouseNames =
            rootFamily!!.spouseTempIds
                .mapNotNull { byId[it] }
                .map { "${it.givenName} ${it.surname}".trim() }
                .toSet()

        println("root spouses=$spouseNames")
        assertTrue(
            spouseNames.any {
                it.contains("MERYEM", ignoreCase = true) ||
                    it.contains("WERYEM", ignoreCase = true)
            },
            "Mother missing: $spouseNames",
        )
        assertTrue(spouseNames.any { it.contains("ADEM", ignoreCase = true) }, "Father missing: $spouseNames")
        assertEquals(2, rootFamily.spouseTempIds.size)
    }
}
