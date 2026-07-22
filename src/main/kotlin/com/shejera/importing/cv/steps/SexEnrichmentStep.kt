package com.shejera.importing.cv.steps

import com.shejera.importing.CvImageRecognizer
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
 * Step 4: detect sex from the left gender-bar color of each box,
 * then enrich the step-3 tree nodes with [LinkedTreeNode.sex].
 *
 * Outputs: tree-with-sex.json, sex-overlay-on-source.png
 */
object SexEnrichmentStep {
    private val log = LoggerFactory.getLogger(SexEnrichmentStep::class.java)

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
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        Files.createDirectories(outputDir)
        CvImageRecognizer.ensureOpenCvLoaded()

        val raw = ImagePreprocessor.loadBgr(sourceImage)
        log.info("[cv-step4] detect sex for {} boxes", boxes.size)
        val sexById = BoxSexDetector.detectAll(raw, boxes)

        val enrichedNodes =
            tree.nodes.map { node ->
                node.copy(sex = sexById[node.id])
            }
        val enriched =
            tree.copy(nodes = enrichedNodes)

        val treeJson = outputDir.resolve("tree-with-sex.json")
        Files.writeString(treeJson, json.encodeToString(enriched))
        log.info(
            "[cv-step4] wrote {} M={} F={} unknown={}",
            treeJson,
            enrichedNodes.count { it.sex == "M" },
            enrichedNodes.count { it.sex == "F" },
            enrichedNodes.count { it.sex == null },
        )

        val overlayOnSource = outputDir.resolve("sex-overlay-on-source.png")
        writeOverlay(raw, boxes, enriched, overlayOnSource)
        log.info("[cv-step4] wrote {}", overlayOnSource)

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
        val sexById = tree.nodes.associate { it.id to it.sex }

        for (box in boxes) {
            val sex = sexById[box.id]
            val color =
                when (sex) {
                    "M" -> Scalar(255.0, 120.0, 0.0) // blue-ish outline
                    "F" -> Scalar(180.0, 0.0, 255.0) // pink outline
                    else -> Scalar(0.0, 180.0, 0.0)
                }
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                2,
            )
            // Mark the sampled strip
            if (!box.isRoot) {
                Imgproc.rectangle(
                    canvas,
                    Point((box.x + 1).toDouble(), (box.y + 2).toDouble()),
                    Point((box.x + 5).toDouble(), (box.yEnd - 2).toDouble()),
                    color,
                    Imgproc.FILLED,
                )
            }
            val label = "${box.id}:${sex ?: "?"}"
            Imgproc.putText(
                canvas,
                label,
                Point(box.x + 8.0, box.y + 18.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                color,
                2,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
