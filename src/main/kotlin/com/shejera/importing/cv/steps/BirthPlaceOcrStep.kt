package com.shejera.importing.cv.steps

import com.shejera.importing.CvImageRecognizer
import com.shejera.importing.cv.BoxOcr
import com.shejera.importing.cv.ImagePreprocessor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Step 8: OCR the last line — Doğum Yeri:…
 *
 * Outputs: tree-with-places.json, places-overlay-on-source.png
 */
object BirthPlaceOcrStep {
    private val log = LoggerFactory.getLogger(BirthPlaceOcrStep::class.java)

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    data class Artifacts(
        val tree: TreeLinkResult,
        val treeJson: Path,
        val overlayOnSource: Path,
    )

    fun run(
        sourceImage: Path,
        boxes: List<DetectedBox>,
        tree: TreeLinkResult,
        outputDir: Path,
        tesseractLanguage: String = "tur+eng",
        tesseractDataPath: String? = null,
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        Files.createDirectories(outputDir)
        CvImageRecognizer.ensureOpenCvLoaded()

        val raw = ImagePreprocessor.loadBgr(sourceImage)
        val ocr = BoxOcr(tesseractLanguage, tesseractDataPath)
        ocr.warmUp()

        log.info("[cv-step8] Doğum Yeri line for {} boxes", boxes.size)
        val placeById = LinkedHashMap<Int, String?>()
        for (box in boxes) {
            val rawLine =
                ocr.readPlaceLineBand(
                    bgr = raw,
                    boxX = box.x,
                    boxY = box.y,
                    boxWidth = box.width,
                    boxHeight = box.height,
                    isRoot = box.isRoot,
                )
            val place = BirthPlaceCleaner.clean(rawLine)
            placeById[box.id] = place
            log.info(
                "[cv-step8] box {} place='{}' raw='{}'",
                box.id,
                place,
                rawLine.replace("\n", " "),
            )
        }

        val enrichedNodes =
            tree.nodes.map { node ->
                node.copy(birthPlace = placeById[node.id])
            }
        val enriched = tree.copy(nodes = enrichedNodes)

        val treeJson = outputDir.resolve("tree-with-places.json")
        Files.writeString(treeJson, json.encodeToString(enriched))
        val withPlace = enrichedNodes.count { !it.birthPlace.isNullOrBlank() }
        log.info("[cv-step8] wrote {} withPlace={}/{}", treeJson, withPlace, enrichedNodes.size)

        val overlayOnSource = outputDir.resolve("places-overlay-on-source.png")
        writeOverlay(raw, boxes, enriched, overlayOnSource)
        log.info("[cv-step8] wrote {}", overlayOnSource)

        return Artifacts(
            tree = enriched,
            treeJson = treeJson,
            overlayOnSource = overlayOnSource,
        )
    }

    private fun writeOverlay(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
        tree: TreeLinkResult,
        outputPath: Path,
    ) {
        val canvas = sourceBgr.clone()
        val byId = tree.nodes.associateBy { it.id }

        for (box in boxes) {
            val node = byId[box.id]
            val color = Scalar(180.0, 80.0, 0.0)
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                1,
            )
            val label = "${box.id}: ${node?.birthPlace ?: "?"}"
            Imgproc.putText(
                canvas,
                label.take(48),
                Point(box.x + 4.0, box.y + 16.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.35,
                color,
                1,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
