package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 5: OCR first line (name) per box.
 *
 * Outputs:
 *   build/cv-artifacts/step5/tree-with-names.json
 *   build/cv-artifacts/step5/names-overlay-on-source.png
 */
class NameOcrStepTest {
    @Test
    fun enrichesTreeWithFirstLineNames() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val step1Dir = Path.of("build", "cv-artifacts", "step1").toAbsolutePath().normalize()
        val step2Dir = Path.of("build", "cv-artifacts", "step2").toAbsolutePath().normalize()
        val step3Dir = Path.of("build", "cv-artifacts", "step3").toAbsolutePath().normalize()
        val step4Dir = Path.of("build", "cv-artifacts", "step4").toAbsolutePath().normalize()
        val step5Dir = Path.of("build", "cv-artifacts", "step5").toAbsolutePath().normalize()
        listOf(step1Dir, step2Dir, step3Dir, step4Dir, step5Dir).forEach { Files.createDirectories(it) }

        val step1 = BoxDetectionStep.run(source, step1Dir)
        val step2 = ArrowDetectionStep.run(source, step1.result.boxes, step2Dir)
        val step3 =
            TreeLinkStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                arrows = step2.result.arrows,
                outputDir = step3Dir,
            )
        val step4 =
            SexEnrichmentStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                tree = step3.tree,
                outputDir = step4Dir,
            )
        val artifacts =
            NameOcrStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                tree = step4.tree,
                outputDir = step5Dir,
            )

        assertTrue(Files.exists(artifacts.treeJson), "tree-with-names.json missing")
        assertTrue(Files.exists(artifacts.overlayOnSource), "names-overlay-on-source.png missing")

        val named = artifacts.tree.nodes.filter { !it.name.isNullOrBlank() }
        assertTrue(
            named.size >= (artifacts.tree.nodeCount * 0.7).toInt(),
            "Expected most boxes to have a name, got ${named.size}/${artifacts.tree.nodeCount}",
        )

        // No leftover standalone dash as the whole name
        assertTrue(named.none { it.name == "-" })

        println("step5 named=${named.size}/${artifacts.tree.nodeCount}")
        println("overlay=${artifacts.overlayOnSource}")
        println("json=${artifacts.treeJson}")
        artifacts.tree.nodes.take(8).forEach {
            println("  id=${it.id} sex=${it.sex} name=${it.name}")
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
