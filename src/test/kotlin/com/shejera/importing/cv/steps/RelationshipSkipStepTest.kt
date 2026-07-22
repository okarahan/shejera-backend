package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 6: relationship line under name (keep L2, skip wrap L3).
 *
 * Outputs:
 *   build/cv-artifacts/step6/tree-with-roles.json
 *   build/cv-artifacts/step6/roles-overlay-on-source.png
 */
class RelationshipSkipStepTest {
    @Test
    fun readsOrSkipsRelationshipLines() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val dirs =
            (1..6).associateWith { n ->
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
        val artifacts =
            RelationshipSkipStep.run(source, step1.result.boxes, step5.tree, dirs.getValue(6))

        assertTrue(Files.exists(artifacts.treeJson))
        assertTrue(Files.exists(artifacts.overlayOnSource))

        val root = artifacts.tree.nodes.first { it.id == 0 }
        assertTrue(root.role == null, "Root should have no relationship role")

        val nonRoot = artifacts.tree.nodes.filter { it.id != 0 }
        val withRole = nonRoot.count { !it.role.isNullOrBlank() }
        assertTrue(
            withRole >= (nonRoot.size * 0.5).toInt(),
            "Expected many relationship labels, got $withRole/${nonRoot.size}",
        )

        println("step6 withRole=$withRole/${nonRoot.size}")
        println("overlay=${artifacts.overlayOnSource}")
        artifacts.tree.nodes.take(8).forEach {
            println("  id=${it.id} name=${it.name} role=${it.role}")
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
