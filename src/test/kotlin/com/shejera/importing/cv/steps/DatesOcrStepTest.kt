package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 7: DT / ÖT date line OCR.
 *
 * Outputs:
 *   build/cv-artifacts/step7/tree-with-dates.json
 *   build/cv-artifacts/step7/dates-overlay-on-source.png
 */
class DatesOcrStepTest {
    @Test
    fun enrichesTreeWithBirthAndDeathDates() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val dirs =
            (1..7).associateWith { n ->
                Path.of("build", "cv-artifacts", "step$n").toAbsolutePath().normalize()
                    .also { Files.createDirectories(it) }
            }

        val step1 = BoxDetectionStep.run(source, dirs.getValue(1))
        val step2 = ArrowDetectionStep.run(source, step1.result.boxes, dirs.getValue(2))
        val step3 =
            TreeLinkStep.run(source, step1.result.boxes, step2.result.arrows, dirs.getValue(3))
        val step4 =
            SexEnrichmentStep.run(source, step1.result.boxes, step3.tree, dirs.getValue(4))
        val step5 =
            NameOcrStep.run(source, step1.result.boxes, step4.tree, dirs.getValue(5))
        val step6 =
            RelationshipSkipStep.run(source, step1.result.boxes, step5.tree, dirs.getValue(6))
        val artifacts =
            DatesOcrStep.run(source, step1.result.boxes, step6.tree, dirs.getValue(7))

        assertTrue(Files.exists(artifacts.treeJson))
        assertTrue(Files.exists(artifacts.overlayOnSource))

        val withBirth = artifacts.tree.nodes.count { !it.birthDate.isNullOrBlank() }
        assertTrue(
            withBirth >= (artifacts.tree.nodeCount * 0.5).toInt(),
            "Expected many birth dates, got $withBirth/${artifacts.tree.nodeCount}",
        )

        // Normalized form DD.MM.YYYY when full date
        val sample =
            artifacts.tree.nodes
                .mapNotNull { it.birthDate }
                .firstOrNull { it.contains('.') }
        if (sample != null) {
            assertTrue(
                Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(sample),
                "Expected zero-padded date, got $sample",
            )
        }

        println("step7 withBirth=$withBirth/${artifacts.tree.nodeCount}")
        println("overlay=${artifacts.overlayOnSource}")
        artifacts.tree.nodes.take(8).forEach {
            println("  id=${it.id} name=${it.name} DT=${it.birthDate} ÖT=${it.deathDate}")
        }
    }

    private fun resolveFixture(): Path? {
        val candidates = listOf("step1-source.png", "step1-source.jpg", "step1-source.jpeg")
        for (name in candidates) {
            val url = javaClass.classLoader.getResource("cv/fixtures/$name") ?: continue
            return Path.of(url.toURI())
        }
        for (name in candidates) {
            val path = Path.of("src/test/resources/cv/fixtures/$name")
            if (Files.exists(path)) return path.toAbsolutePath()
        }
        return null
    }
}
