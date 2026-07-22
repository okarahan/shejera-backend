package com.shejera.importing.cv.steps

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Step 2: detect arrows as start + tip (no box linking).
 *
 * Uses step 1 boxes only to erase card areas on the source image.
 *
 * Outputs:
 *   build/cv-artifacts/step2/arrows-only.png
 *   build/cv-artifacts/step2/arrows-debug.png
 *   build/cv-artifacts/step2/arrows-overlay-on-source.png  ← original + arrows
 *   build/cv-artifacts/step2/arrows.json
 */
class ArrowDetectionStepTest {
    @Test
    fun detectsArrowsAsStartAndTip() {
        val source =
            resolveFixture()
                ?: fail("Missing step1-source fixture under src/test/resources/cv/fixtures/")

        val step1Dir = Path.of("build", "cv-artifacts", "step1").toAbsolutePath().normalize()
        val step2Dir = Path.of("build", "cv-artifacts", "step2").toAbsolutePath().normalize()
        Files.createDirectories(step1Dir)
        Files.createDirectories(step2Dir)

        val step1 = BoxDetectionStep.run(source, step1Dir)
        val artifacts = ArrowDetectionStep.run(source, step1.result.boxes, step2Dir)

        assertTrue(Files.exists(artifacts.arrowsOnlyImage), "arrows-only.png missing")
        assertTrue(Files.exists(artifacts.arrowsDebugImage), "arrows-debug.png missing")
        assertTrue(Files.exists(artifacts.overlayOnSource), "arrows-overlay-on-source.png missing")
        assertTrue(Files.exists(artifacts.arrowsJson), "arrows.json missing")

        val result = artifacts.result
        assertTrue(
            result.arrowCount >= 10,
            "Expected many arrows on Nüfus chart, got ${result.arrowCount}",
        )

        for (arrow in result.arrows) {
            assertTrue(arrow.straightLength >= 25, "arrow ${arrow.id} too short")
            // Tip should be below (or at) start — arrows point downward into child boxes
            assertTrue(
                arrow.tipY >= arrow.startY - 5,
                "arrow ${arrow.id}: tip should be at/below start " +
                    "(start=${arrow.startY} tip=${arrow.tipY})",
            )
        }

        println("step2 arrows=${result.arrowCount}")
        println("overlay=${artifacts.overlayOnSource}")
        println("only=${artifacts.arrowsOnlyImage}")
        println("debug=${artifacts.arrowsDebugImage}")
        println("json=${artifacts.arrowsJson}")
        result.arrows.take(8).forEach {
            println(
                "  a${it.id} start=(${it.startX},${it.startY}) " +
                    "tip=(${it.tipX},${it.tipY}) len=${"%.0f".format(it.straightLength)}",
            )
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
