package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 4: gender from left bar color → enrich tree nodes with sex.
 *
 * Outputs:
 *   build/cv-artifacts/step4/tree-with-sex.json
 *   build/cv-artifacts/step4/sex-overlay-on-source.png
 */
class SexEnrichmentStepTest {
    @Test
    fun enrichesTreeNodesWithSexFromGenderBar() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val step1Dir = Path.of("build", "cv-artifacts", "step1").toAbsolutePath().normalize()
        val step2Dir = Path.of("build", "cv-artifacts", "step2").toAbsolutePath().normalize()
        val step3Dir = Path.of("build", "cv-artifacts", "step3").toAbsolutePath().normalize()
        val step4Dir = Path.of("build", "cv-artifacts", "step4").toAbsolutePath().normalize()
        Files.createDirectories(step1Dir)
        Files.createDirectories(step2Dir)
        Files.createDirectories(step3Dir)
        Files.createDirectories(step4Dir)

        val step1 = BoxDetectionStep.run(source, step1Dir)
        val step2 = ArrowDetectionStep.run(source, step1.result.boxes, step2Dir)
        val step3 =
            TreeLinkStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                arrows = step2.result.arrows,
                outputDir = step3Dir,
            )
        val artifacts =
            SexEnrichmentStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                tree = step3.tree,
                outputDir = step4Dir,
            )

        assertTrue(Files.exists(artifacts.treeJson), "tree-with-sex.json missing")
        assertTrue(Files.exists(artifacts.overlayOnSource), "sex-overlay-on-source.png missing")

        val nodes = artifacts.tree.nodes
        val withSex = nodes.filter { it.id != artifacts.tree.rootId }
        val male = withSex.count { it.sex == "M" }
        val female = withSex.count { it.sex == "F" }
        val unknown = withSex.count { it.sex == null }

        assertTrue(male >= 5, "Expected several males, got $male")
        assertTrue(female >= 5, "Expected several females, got $female")
        assertTrue(
            unknown <= 2,
            "Too many unknown sex among non-root: $unknown (M=$male F=$female)",
        )

        // Root has no gender bar
        val root = nodes.first { it.id == artifacts.tree.rootId }
        assertTrue(root.sex == null, "Root should have null sex")

        // Parents row: typically father(M) + mother(F) under root
        val parents = nodes.filter { it.parentId == 0 }
        assertTrue(parents.size >= 2, "Expected ≥2 children of root")
        assertTrue(
            parents.any { it.sex == "M" } && parents.any { it.sex == "F" },
            "Expected both M and F under root, got ${parents.map { it.id to it.sex }}",
        )

        println("step4 M=$male F=$female unknown=$unknown")
        println("overlay=${artifacts.overlayOnSource}")
        println("json=${artifacts.treeJson}")
        nodes.take(8).forEach {
            println("  id=${it.id} parent=${it.parentId} sex=${it.sex}")
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
