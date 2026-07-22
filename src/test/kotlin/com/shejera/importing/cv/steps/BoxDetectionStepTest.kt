package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 1: detect person boxes only.
 *
 * Place the source image at:
 *   src/test/resources/cv/fixtures/step1-source.png
 *   (or step1-source.jpg)
 *
 * Outputs (for visual inspection):
 *   build/cv-artifacts/step1/boxes-only.png
 *   build/cv-artifacts/step1/boxes-overlay-on-source.png  ← JSON boxes on original
 *   build/cv-artifacts/step1/boxes.json
 *   build/cv-artifacts/step1/boxes-validation.json        ← edge/color checks
 */
class BoxDetectionStepTest {
    @Test
    fun detectsBoxesAndWritesArtifacts() {
        val source =
            resolveFixture()
                ?: fail(
                    "Missing fixture. Copy your chart to " +
                        "src/test/resources/cv/fixtures/step1-source.png " +
                        "(see fixtures/README.md)",
                )

        val outputDir =
            Path.of("build", "cv-artifacts", "step1").toAbsolutePath().normalize()
        Files.createDirectories(outputDir)

        val artifacts = BoxDetectionStep.run(source, outputDir)

        assertTrue(Files.exists(artifacts.boxesOnlyImage), "boxes-only.png missing")
        assertTrue(Files.exists(artifacts.boxesJson), "boxes.json missing")
        assertTrue(Files.exists(artifacts.overlayOnSource), "boxes-overlay-on-source.png missing")
        assertTrue(Files.exists(artifacts.validationReport), "boxes-validation.json missing")
        assertTrue(
            artifacts.result.boxCount >= 1,
            "Expected at least one box, got ${artifacts.result.boxCount}",
        )
        val root = artifacts.result.boxes.firstOrNull { it.isRoot }
        assertTrue(root != null, "Expected a root box")
        checkNotNull(root)
        assertTrue(root.id == 0, "Root should be id 0, was ${root.id}")

        // Non-root ids increase in reading order (already encoded in list order after root)
        val nonRoot = artifacts.result.boxes.filter { !it.isRoot }
        assertTrue(
            nonRoot.zipWithNext().all { (a, b) -> a.id < b.id },
            "Non-root ids should increase",
        )

        // Same-row neighbors must keep a visible white gap (not bleed into each other)
        val rowTol = 40
        for ((_, row) in nonRoot.groupBy { it.centerY / rowTol }) {
            val sorted = row.sortedBy { it.x }
            for ((a, b) in sorted.zipWithNext()) {
                val gap = b.x - a.xEnd
                assertTrue(
                    gap >= 8,
                    "Boxes ${a.id} and ${b.id} too close: gap=$gap (a.xEnd=${a.xEnd} b.x=${b.x})",
                )
            }
        }

        // JSON geometry must match pale-cyan cards on the Ausgangsbild
        val failed = artifacts.validation.boxes.filter { !it.passed }
        assertTrue(
            artifacts.validation.allPassed,
            "Box geometry mismatch vs source. Failed: " +
                failed.joinToString { "id=${it.id} notes=${it.notes}" },
        )

        println("step1 boxes=${artifacts.result.boxCount}")
        println("overlay=${artifacts.overlayOnSource}")
        println("validation=${artifacts.validationReport} passed=${artifacts.validation.passedCount}/${artifacts.validation.checkedCount}")
        println("image=${artifacts.boxesOnlyImage}")
        println("json=${artifacts.boxesJson}")
        artifacts.result.boxes.take(5).forEach { box ->
            println(
                "  id=${box.id} root=${box.isRoot} " +
                    "xy=(${box.x},${box.y}) size=${box.width}x${box.height} " +
                    "end=(${box.xEnd},${box.yEnd})",
            )
        }
    }

    private fun resolveFixture(): Path? {
        val candidates =
            listOf(
                "step1-source.png",
                "step1-source.jpg",
                "step1-source.jpeg",
            )
        for (name in candidates) {
            val url = javaClass.classLoader.getResource("cv/fixtures/$name") ?: continue
            return Path.of(url.toURI())
        }
        // Also allow a file sitting next to resources during local iteration
        for (name in candidates) {
            val path = Path.of("src/test/resources/cv/fixtures/$name")
            if (Files.exists(path)) return path.toAbsolutePath()
        }
        return null
    }
}
