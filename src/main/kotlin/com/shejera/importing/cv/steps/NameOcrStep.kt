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
 * Step 5: OCR the first line of each box (person name only).
 * Enriches tree nodes with [LinkedTreeNode.name].
 *
 * Outputs: tree-with-names.json, names-overlay-on-source.png
 */
object NameOcrStep {
    private val log = LoggerFactory.getLogger(NameOcrStep::class.java)

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

        log.info("[cv-step5] OCR first-line names for {} boxes", boxes.size)
        val nameById = LinkedHashMap<Int, String?>()
        for (box in boxes) {
            val rawLine =
                ocr.readFirstLineName(
                    bgr = raw,
                    boxX = box.x,
                    boxY = box.y,
                    boxWidth = box.width,
                    boxHeight = box.height,
                    isRoot = box.isRoot,
                )
            val name = NameLineCleaner.clean(rawLine)
            nameById[box.id] = name
            log.info("[cv-step5] box {} name='{}' raw='{}'", box.id, name, rawLine.replace('\n', ' '))
        }

        val enrichedNodes =
            tree.nodes.map { node ->
                node.copy(name = nameById[node.id])
            }
        val enriched = tree.copy(nodes = enrichedNodes)

        val treeJson = outputDir.resolve("tree-with-names.json")
        Files.writeString(treeJson, json.encodeToString(enriched))
        val named = enrichedNodes.count { !it.name.isNullOrBlank() }
        log.info("[cv-step5] wrote {} named={}/{}", treeJson, named, enrichedNodes.size)

        val overlayOnSource = outputDir.resolve("names-overlay-on-source.png")
        writeOverlay(raw, boxes, enriched, overlayOnSource)
        log.info("[cv-step5] wrote {}", overlayOnSource)

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
        val nameById = tree.nodes.associate { it.id to it.name }

        for (box in boxes) {
            val name = nameById[box.id]
            val color = Scalar(0.0, 140.0, 255.0)
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                1,
            )
            val label = "${box.id}: ${name ?: "?"}"
            Imgproc.putText(
                canvas,
                label.take(42),
                Point(box.x + 6.0, box.y + 16.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.4,
                color,
                1,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
