package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 8: Doğum Yeri (birth place) OCR.
 *
 * Outputs:
 *   build/cv-artifacts/step8/tree-with-places.json
 *   build/cv-artifacts/step8/places-overlay-on-source.png
 */
class BirthPlaceOcrStepTest {
    @Test
    fun enrichesTreeWithBirthPlaces() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val dirs =
            (1..8).associateWith { n ->
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
        val step7 =
            DatesOcrStep.run(source, step1.result.boxes, step6.tree, dirs.getValue(7))
        val artifacts =
            BirthPlaceOcrStep.run(source, step1.result.boxes, step7.tree, dirs.getValue(8))

        assertTrue(Files.exists(artifacts.treeJson))
        assertTrue(Files.exists(artifacts.overlayOnSource))

        val withPlace = artifacts.tree.nodes.count { !it.birthPlace.isNullOrBlank() }
        assertTrue(
            withPlace >= (artifacts.tree.nodeCount * 0.4).toInt(),
            "Expected many birth places, got $withPlace/${artifacts.tree.nodeCount}",
        )

        println("step8 withPlace=$withPlace/${artifacts.tree.nodeCount}")
        println("overlay=${artifacts.overlayOnSource}")
        artifacts.tree.nodes.take(8).forEach {
            println("  id=${it.id} name=${it.name} place=${it.birthPlace}")
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
