package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 3: link boxes + arrows → parent tree.
 *
 * Outputs:
 *   build/cv-artifacts/step3/tree.json
 *   build/cv-artifacts/step3/tree-overlay-on-source.png
 */
class TreeLinkStepTest {
    @Test
    fun linksBoxesToParentsViaArrows() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val step1Dir = Path.of("build", "cv-artifacts", "step1").toAbsolutePath().normalize()
        val step2Dir = Path.of("build", "cv-artifacts", "step2").toAbsolutePath().normalize()
        val step3Dir = Path.of("build", "cv-artifacts", "step3").toAbsolutePath().normalize()
        Files.createDirectories(step1Dir)
        Files.createDirectories(step2Dir)
        Files.createDirectories(step3Dir)

        val step1 = BoxDetectionStep.run(source, step1Dir)
        val step2 = ArrowDetectionStep.run(source, step1.result.boxes, step2Dir)
        val artifacts =
            TreeLinkStep.run(
                sourceImage = source,
                boxes = step1.result.boxes,
                arrows = step2.result.arrows,
                outputDir = step3Dir,
            )

        assertTrue(Files.exists(artifacts.treeJson), "tree.json missing")
        assertTrue(Files.exists(artifacts.overlayOnSource), "tree-overlay-on-source.png missing")

        val tree = artifacts.tree
        assertEquals(0, tree.rootId)

        val root = tree.nodes.first { it.id == 0 }
        assertEquals(null, root.parentId)
        assertTrue(root.childIds.size >= 2, "Root should have ≥2 children, got ${root.childIds}")
        assertTrue(root.childIds.contains(1), "Expected box 1 under root")
        assertTrue(root.childIds.contains(2), "Expected box 2 under root")

        assertTrue(
            tree.orphanIds.isEmpty(),
            "Orphan boxes without parent: ${tree.orphanIds}",
        )

        val byId = tree.nodes.associateBy { it.id }
        for (node in tree.nodes) {
            val p = node.parentId ?: continue
            assertTrue(byId.containsKey(p), "parent $p missing for child ${node.id}")
            assertTrue(
                byId.getValue(p).childIds.contains(node.id),
                "parent $p childIds missing ${node.id}",
            )
        }

        // Box 1 and 2 parents are root
        assertEquals(0, byId.getValue(1).parentId)
        assertEquals(0, byId.getValue(2).parentId)

        println("step3 edges=${tree.edgeCount} orphans=${tree.orphanIds}")
        println("root children=${root.childIds}")
        println("overlay=${artifacts.overlayOnSource}")
        println("json=${artifacts.treeJson}")
        tree.nodes.take(10).forEach {
            println("  id=${it.id} parent=${it.parentId} children=${it.childIds} arrow=${it.arrowId}")
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
