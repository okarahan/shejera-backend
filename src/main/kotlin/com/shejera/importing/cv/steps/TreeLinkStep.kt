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
 * Step 3: link step-1 boxes with step-2 arrows → chart parent tree.
 *
 * Outputs: tree.json, tree-overlay-on-source.png
 */
object TreeLinkStep {
    private val log = LoggerFactory.getLogger(TreeLinkStep::class.java)

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
        arrows: List<DetectedArrow>,
        outputDir: Path,
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        require(boxes.isNotEmpty()) { "Step 1 boxes required" }
        Files.createDirectories(outputDir)
        CvImageRecognizer.ensureOpenCvLoaded()

        log.info("[cv-step3] link {} boxes with {} arrows", boxes.size, arrows.size)
        val links = BoxArrowLinker.link(boxes, arrows)
        val tree =
            BoxArrowLinker.toTreeResult(
                sourceFile = sourceImage.fileName.toString(),
                boxes = boxes,
                links = links,
            )

        val treeJson = outputDir.resolve("tree.json")
        Files.writeString(treeJson, json.encodeToString(tree))
        log.info(
            "[cv-step3] wrote {} edges={} orphans={}",
            treeJson,
            tree.edgeCount,
            tree.orphanIds,
        )

        val raw = ImagePreprocessor.loadBgr(sourceImage)
        val overlayOnSource = outputDir.resolve("tree-overlay-on-source.png")
        writeOverlay(raw, boxes, arrows, tree, overlayOnSource)
        log.info("[cv-step3] wrote {}", overlayOnSource)

        return Artifacts(
            tree = tree,
            treeJson = treeJson,
            overlayOnSource = overlayOnSource,
        )
    }

    private fun writeOverlay(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
        arrows: List<DetectedArrow>,
        tree: TreeLinkResult,
        outputPath: Path,
    ) {
        val canvas = sourceBgr.clone()
        val byId = boxes.associateBy { it.id }
        val arrowById = arrows.associateBy { it.id }

        for (box in boxes) {
            val color =
                if (box.isRoot) {
                    Scalar(0.0, 165.0, 255.0)
                } else {
                    Scalar(0.0, 180.0, 0.0)
                }
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                2,
            )
            Imgproc.putText(
                canvas,
                box.id.toString(),
                Point(box.x + 4.0, box.y + 18.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.55,
                color,
                2,
            )
        }

        for (node in tree.nodes) {
            val parentId = node.parentId ?: continue
            val child = byId[node.id] ?: continue
            val parent = byId[parentId] ?: continue
            val arrow = node.arrowId?.let { arrowById[it] }

            // Prefer drawing along detected start→tip; fallback geometric line
            if (arrow != null) {
                Imgproc.arrowedLine(
                    canvas,
                    Point(arrow.startX.toDouble(), arrow.startY.toDouble()),
                    Point(arrow.tipX.toDouble(), arrow.tipY.toDouble()),
                    Scalar(255.0, 0.0, 255.0),
                    2,
                    Imgproc.LINE_AA,
                    0,
                    0.12,
                )
            } else {
                Imgproc.arrowedLine(
                    canvas,
                    Point(parent.centerX.toDouble(), parent.yEnd.toDouble()),
                    Point(child.centerX.toDouble(), child.y.toDouble()),
                    Scalar(255.0, 0.0, 255.0),
                    2,
                    Imgproc.LINE_AA,
                    0,
                    0.12,
                )
            }

            Imgproc.putText(
                canvas,
                "${node.id}←${parentId}",
                Point(child.centerX.toDouble() - 20, child.y - 8.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.4,
                Scalar(200.0, 0.0, 200.0),
                1,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
