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
 * Step 2: erase boxes/watermark from the source image, keep arrows only,
 * return each arrow as start + tip (no box linking — that is step 3).
 *
 * Inputs: source image + boxes from step 1 (positions/ids used only to erase cards).
 * Outputs: arrows-only.png, arrows-debug.png, arrows-overlay-on-source.png,
 * arrows.json, [ArrowDetectionResult].
 */
object ArrowDetectionStep {
    private val log = LoggerFactory.getLogger(ArrowDetectionStep::class.java)

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    data class Artifacts(
        val result: ArrowDetectionResult,
        val arrowsOnlyImage: Path,
        val arrowsDebugImage: Path,
        val overlayOnSource: Path,
        val arrowsJson: Path,
    )

    fun run(
        sourceImage: Path,
        boxes: List<DetectedBox>,
        outputDir: Path,
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        require(boxes.isNotEmpty()) { "Step 1 boxes required to erase card areas" }
        Files.createDirectories(outputDir)
        CvImageRecognizer.ensureOpenCvLoaded()

        val raw = ImagePreprocessor.loadBgr(sourceImage)
        log.info("[cv-step2] erase boxes/watermark, keep arrows ({} boxes)", boxes.size)

        val arrowsOnly = ArrowMaskBuilder.buildArrowsOnlyBgr(raw, boxes)
        val arrowsOnlyImage = outputDir.resolve("arrows-only.png")
        Imgcodecs.imwrite(arrowsOnlyImage.toAbsolutePath().toString(), arrowsOnly)
        log.info("[cv-step2] wrote {}", arrowsOnlyImage)

        val inkMask = ArrowMaskBuilder.buildArrowInkMask(raw, boxes)
        val arrows = ArrowGeometryDetector.detect(inkMask, boxes)

        val result =
            ArrowDetectionResult(
                sourceFile = sourceImage.fileName.toString(),
                imageWidth = raw.cols(),
                imageHeight = raw.rows(),
                arrowCount = arrows.size,
                arrows = arrows,
            )

        val arrowsJson = outputDir.resolve("arrows.json")
        Files.writeString(arrowsJson, json.encodeToString(result))
        log.info("[cv-step2] wrote {} ({} arrows)", arrowsJson, arrows.size)

        val arrowsDebugImage = outputDir.resolve("arrows-debug.png")
        writeDebugOverlay(arrowsOnly, boxes, arrows, arrowsDebugImage)
        log.info("[cv-step2] wrote {}", arrowsDebugImage)

        val overlayOnSource = outputDir.resolve("arrows-overlay-on-source.png")
        writeOverlayOnSource(raw, inkMask, boxes, arrows, overlayOnSource)
        log.info("[cv-step2] wrote {}", overlayOnSource)

        return Artifacts(
            result = result,
            arrowsOnlyImage = arrowsOnlyImage,
            arrowsDebugImage = arrowsDebugImage,
            overlayOnSource = overlayOnSource,
            arrowsJson = arrowsJson,
        )
    }

    /**
     * Original chart with real arrow ink tinted + start→tip segments from step 2.
     */
    private fun writeOverlayOnSource(
        sourceBgr: Mat,
        inkMask: Mat,
        boxes: List<DetectedBox>,
        arrows: List<DetectedArrow>,
        outputPath: Path,
    ) {
        val canvas = sourceBgr.clone()

        // Highlight detected arrow ink in red so the real path is visible
        canvas.setTo(Scalar(0.0, 0.0, 220.0), inkMask)

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
                1,
            )
        }

        for (arrow in arrows) {
            Imgproc.arrowedLine(
                canvas,
                Point(arrow.startX.toDouble(), arrow.startY.toDouble()),
                Point(arrow.tipX.toDouble(), arrow.tipY.toDouble()),
                Scalar(255.0, 0.0, 255.0),
                2,
                Imgproc.LINE_AA,
                0,
                0.15,
            )
            Imgproc.circle(
                canvas,
                Point(arrow.startX.toDouble(), arrow.startY.toDouble()),
                6,
                Scalar(255.0, 128.0, 0.0),
                Imgproc.FILLED,
            )
            Imgproc.circle(
                canvas,
                Point(arrow.tipX.toDouble(), arrow.tipY.toDouble()),
                6,
                Scalar(0.0, 255.0, 0.0),
                Imgproc.FILLED,
            )
            Imgproc.putText(
                canvas,
                "a${arrow.id}",
                Point(arrow.tipX + 8.0, arrow.tipY - 6.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 0.0, 255.0),
                2,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }

    /**
     * Arrows-only background + box outlines (from step 1) + start→tip segments.
     */
    private fun writeDebugOverlay(
        arrowsOnlyBgr: Mat,
        boxes: List<DetectedBox>,
        arrows: List<DetectedArrow>,
        outputPath: Path,
    ) {
        val canvas = arrowsOnlyBgr.clone()

        for (box in boxes) {
            val color =
                if (box.isRoot) {
                    Scalar(0.0, 165.0, 255.0)
                } else {
                    Scalar(200.0, 200.0, 200.0)
                }
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                1,
            )
            Imgproc.putText(
                canvas,
                box.id.toString(),
                Point(box.x + 3.0, box.y + 14.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.45,
                color,
                1,
            )
        }

        for (arrow in arrows) {
            Imgproc.arrowedLine(
                canvas,
                Point(arrow.startX.toDouble(), arrow.startY.toDouble()),
                Point(arrow.tipX.toDouble(), arrow.tipY.toDouble()),
                Scalar(0.0, 0.0, 220.0),
                2,
                Imgproc.LINE_AA,
                0,
                0.12,
            )
            Imgproc.circle(
                canvas,
                Point(arrow.startX.toDouble(), arrow.startY.toDouble()),
                5,
                Scalar(255.0, 120.0, 0.0),
                Imgproc.FILLED,
            )
            Imgproc.circle(
                canvas,
                Point(arrow.tipX.toDouble(), arrow.tipY.toDouble()),
                5,
                Scalar(0.0, 200.0, 0.0),
                Imgproc.FILLED,
            )
            Imgproc.putText(
                canvas,
                "a${arrow.id}",
                Point(arrow.tipX + 6.0, arrow.tipY - 4.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.4,
                Scalar(0.0, 0.0, 180.0),
                1,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
