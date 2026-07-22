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
 * Step 7: OCR the DT / ÖT date line on each card.
 *
 * Outputs: tree-with-dates.json, dates-overlay-on-source.png
 */
object DatesOcrStep {
    private val log = LoggerFactory.getLogger(DatesOcrStep::class.java)

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

        log.info("[cv-step7] DT/ÖT date line for {} boxes", boxes.size)
        val datesById = LinkedHashMap<Int, DateLineCleaner.Dates>()
        for (box in boxes) {
            val rawLine =
                ocr.readDateLineBand(
                    bgr = raw,
                    boxX = box.x,
                    boxY = box.y,
                    boxWidth = box.width,
                    boxHeight = box.height,
                    isRoot = box.isRoot,
                )
            val dates = DateLineCleaner.clean(rawLine)
            datesById[box.id] = dates
            log.info(
                "[cv-step7] box {} birth={} death={} raw='{}'",
                box.id,
                dates.birthDate,
                dates.deathDate,
                rawLine.replace("\n", " "),
            )
        }

        val enrichedNodes =
            tree.nodes.map { node ->
                val d = datesById[node.id]
                node.copy(
                    birthDate = d?.birthDate,
                    deathDate = d?.deathDate,
                )
            }
        val enriched = tree.copy(nodes = enrichedNodes)

        val treeJson = outputDir.resolve("tree-with-dates.json")
        Files.writeString(treeJson, json.encodeToString(enriched))
        val withBirth = enrichedNodes.count { !it.birthDate.isNullOrBlank() }
        log.info("[cv-step7] wrote {} withBirth={}/{}", treeJson, withBirth, enrichedNodes.size)

        val overlayOnSource = outputDir.resolve("dates-overlay-on-source.png")
        writeOverlay(raw, boxes, enriched, overlayOnSource)
        log.info("[cv-step7] wrote {}", overlayOnSource)

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
            val color = Scalar(0.0, 100.0, 200.0)
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                1,
            )
            val label =
                "${box.id}: DT ${node?.birthDate ?: "-"} / ÖT ${node?.deathDate ?: "-"}"
            Imgproc.putText(
                canvas,
                label.take(52),
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
