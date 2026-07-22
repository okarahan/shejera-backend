package com.shejera.importing.cv

import com.shejera.importing.CvImageRecognizer
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ShapeDetectorSampleTest {
    @Test
    fun detectsMultipleCardsOnNufusSampleIfPresent() {
        val candidates =
            listOf(
                Path.of("debug-import.png"),
                Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "shejera-imports",
                ).let { dir ->
                    dir.toFile().listFiles()?.maxByOrNull { it.lastModified() }?.toPath()
                },
            ).mapNotNull { it }

        val imagePath =
            candidates.firstOrNull { java.nio.file.Files.exists(it) }
                ?: return // skip when sample not available in CI

        CvImageRecognizer.ensureOpenCvLoaded()
        val bgr = ImagePreprocessor.loadBgr(imagePath)
        val cards = ShapeDetector.detectCards(bgr)

        println("sample=$imagePath size=${bgr.cols()}x${bgr.rows()} cards=${cards.size}")
        cards.take(10).forEach {
            println("  ${it.id} root=${it.isRootCircle} sex=${it.sexFromColor} ${it.x},${it.y} ${it.width}x${it.height}")
        }

        assertTrue(
            cards.size >= 10,
            "Expected many person cards on Nüfus chart, got ${cards.size}",
        )
        assertTrue(cards.any { it.isRootCircle }, "Expected a yellow root card")
    }
}
