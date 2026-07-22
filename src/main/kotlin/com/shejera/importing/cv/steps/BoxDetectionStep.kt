package com.shejera.importing.cv.steps

import com.shejera.importing.CvImageRecognizer
import com.shejera.importing.cv.ImagePreprocessor
import com.shejera.importing.cv.ShapeDetector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Step 1 of the CV pipeline: locate person boxes only.
 *
 * Intentionally ignores (does not OCR / link):
 * - watermark (suppressed before detection)
 * - connector lines / arrows
 * - text inside boxes
 *
 * Detection uses coloured gender bars + yellow root highlight as structural cues.
 */
object BoxDetectionStep {
    private val log = LoggerFactory.getLogger(BoxDetectionStep::class.java)

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    data class Artifacts(
        val result: BoxDetectionResult,
        val boxesOnlyImage: Path,
        val boxesJson: Path,
        val overlayOnSource: Path,
        val validationReport: Path,
        val validation: BoxGeometryValidator.ValidationReport,
    )

    /**
     * Run step 1 on [sourceImage] and write debug artifacts under [outputDir].
     */
    fun run(
        sourceImage: Path,
        outputDir: Path,
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        Files.createDirectories(outputDir)

        CvImageRecognizer.ensureOpenCvLoaded()

        log.info("[cv-step1] load {}", sourceImage)
        val raw = ImagePreprocessor.loadBgr(sourceImage)
        val cleaned = ImagePreprocessor.suppressGrayWatermark(raw)

        log.info("[cv-step1] detect boxes (ignore lines/text/watermark)")
        val cards = ShapeDetector.detectCards(cleaned)
        val unordered =
            cards.map { card ->
                DetectedBox(
                    id = -1,
                    x = card.x,
                    y = card.y,
                    width = card.width,
                    height = card.height,
                    xEnd = card.x + card.width - 1,
                    yEnd = card.y + card.height - 1,
                    centerX = card.x + card.width / 2,
                    centerY = card.y + card.height / 2,
                    isRoot = card.isRootCircle,
                )
            }
        // Root = 0; then rows top→bottom, within each row left→right
        val boxes = assignReadingOrderIds(unordered)

        val result =
            BoxDetectionResult(
                sourceFile = sourceImage.fileName.toString(),
                imageWidth = cleaned.cols(),
                imageHeight = cleaned.rows(),
                boxCount = boxes.size,
                boxes = boxes,
            )

        val boxesJson = outputDir.resolve("boxes.json")
        Files.writeString(boxesJson, json.encodeToString(result))
        log.info("[cv-step1] wrote {} ({} boxes)", boxesJson, boxes.size)

        val boxesOnlyImage = outputDir.resolve("boxes-only.png")
        writeBoxesOnlyImage(cleaned, boxes, boxesOnlyImage)
        log.info("[cv-step1] wrote {}", boxesOnlyImage)

        // Visual + metric check against the original Ausgangsbild
        val overlayOnSource = outputDir.resolve("boxes-overlay-on-source.png")
        BoxGeometryValidator.writeOverlayOnSource(raw, boxes, overlayOnSource)
        log.info("[cv-step1] wrote {}", overlayOnSource)

        val validation = BoxGeometryValidator.validate(raw, result)
        val validationReport = outputDir.resolve("boxes-validation.json")
        BoxGeometryValidator.writeReport(validation, validationReport)
        log.info(
            "[cv-step1] validation passed={}/{} allPassed={}",
            validation.passedCount,
            validation.checkedCount,
            validation.allPassed,
        )

        return Artifacts(
            result = result,
            boxesOnlyImage = boxesOnlyImage,
            boxesJson = boxesJson,
            overlayOnSource = overlayOnSource,
            validationReport = validationReport,
            validation = validation,
        )
    }

    /**
     * Numbering: root → 0, then remaining boxes by row (top→bottom), left→right within each row.
     */
    fun assignReadingOrderIds(boxes: List<DetectedBox>): List<DetectedBox> {
        val roots = boxes.filter { it.isRoot }.sortedBy { it.centerX }
        val others = sortByRowsThenLeftToRight(boxes.filter { !it.isRoot })

        var nextId = 0
        return buildList {
            for (root in roots) {
                add(root.copy(id = nextId++))
            }
            for (box in others) {
                add(box.copy(id = nextId++))
            }
        }
    }

    /**
     * Cluster boxes into horizontal rows by [DetectedBox.centerY], then sort each row by x.
     */
    internal fun sortByRowsThenLeftToRight(boxes: List<DetectedBox>): List<DetectedBox> {
        if (boxes.isEmpty()) return emptyList()
        val byY = boxes.sortedBy { it.centerY }
        val medianHeight = byY.map { it.height }.sorted()[byY.size / 2]
        val rowThreshold = maxOf((medianHeight * 0.6).toInt(), 40)

        val rows = mutableListOf<MutableList<DetectedBox>>()
        for (box in byY) {
            val current = rows.lastOrNull()
            val rowCenterY = current?.map { it.centerY }?.average() ?: Double.NaN
            if (current != null && kotlin.math.abs(box.centerY - rowCenterY) <= rowThreshold) {
                current.add(box)
            } else {
                rows.add(mutableListOf(box))
            }
        }
        return rows.flatMap { row -> row.sortedBy { it.centerX } }
    }

    /**
     * White canvas; only the box regions from the source are visible (plus green outlines).
     * Text inside boxes is kept — step 1 only needs geometry; content helps visual checks.
     */
    fun writeBoxesOnlyImage(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
        outputPath: Path,
    ) {
        val canvas = Mat(sourceBgr.size(), sourceBgr.type(), Scalar(255.0, 255.0, 255.0))
        for (box in boxes) {
            val x = box.x.coerceIn(0, sourceBgr.cols() - 1)
            val y = box.y.coerceIn(0, sourceBgr.rows() - 1)
            val w = box.width.coerceAtMost(sourceBgr.cols() - x).coerceAtLeast(1)
            val h = box.height.coerceAtMost(sourceBgr.rows() - y).coerceAtLeast(1)
            val roi = Rect(x, y, w, h)
            sourceBgr.submat(roi).copyTo(canvas.submat(roi))

            val color =
                if (box.isRoot) {
                    Scalar(0.0, 200.0, 255.0) // orange-ish for root
                } else {
                    Scalar(0.0, 180.0, 0.0) // green
                }
            Imgproc.rectangle(
                canvas,
                Point(x.toDouble(), y.toDouble()),
                Point((x + w - 1).toDouble(), (y + h - 1).toDouble()),
                color,
                2,
            )
            Imgproc.putText(
                canvas,
                box.id.toString(),
                Point(x + 4.0, y + 18.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.6,
                color,
                2,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
