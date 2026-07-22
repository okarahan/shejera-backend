package com.shejera.importing.cv.steps

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that JSON box geometry matches the pale-cyan cards on the source image.
 *
 * For each non-root box:
 * - just inside the left edge → gender bar (pink/blue)
 * - mid interior → pale cyan card body (or ink)
 * - just outside the right edge → white background
 * - just outside left / top / bottom → mostly white
 */
object BoxGeometryValidator {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    @Serializable
    data class BoxCheck(
        val id: Int,
        val passed: Boolean,
        val leftGenderBar: Double,
        val interiorCard: Double,
        val outsideRightWhite: Double,
        val outsideLeftWhite: Double,
        val outsideTopWhite: Double,
        val outsideBottomWhite: Double,
        val notes: List<String> = emptyList(),
    )

    @Serializable
    data class ValidationReport(
        val sourceFile: String,
        val boxCount: Int,
        val checkedCount: Int,
        val passedCount: Int,
        val failedCount: Int,
        val allPassed: Boolean,
        val boxes: List<BoxCheck>,
    )

    fun validate(
        sourceBgr: Mat,
        result: BoxDetectionResult,
    ): ValidationReport {
        val hsv = Mat()
        Imgproc.cvtColor(sourceBgr, hsv, Imgproc.COLOR_BGR2HSV)

        val checks =
            result.boxes.map { box ->
                if (box.isRoot) {
                    // Root is a yellow circle — different geometry; skip strict card checks
                    BoxCheck(
                        id = box.id,
                        passed = true,
                        leftGenderBar = 1.0,
                        interiorCard = 1.0,
                        outsideRightWhite = 1.0,
                        outsideLeftWhite = 1.0,
                        outsideTopWhite = 1.0,
                        outsideBottomWhite = 1.0,
                        notes = listOf("root skipped (yellow circle)"),
                    )
                } else {
                    checkCardBox(sourceBgr, hsv, box)
                }
            }

        val checked = checks.filter { it.notes.none { n -> n.startsWith("root") } }
        val passed = checked.count { it.passed }
        return ValidationReport(
            sourceFile = result.sourceFile,
            boxCount = result.boxCount,
            checkedCount = checked.size,
            passedCount = passed,
            failedCount = checked.size - passed,
            allPassed = checked.all { it.passed },
            boxes = checks,
        )
    }

    fun writeReport(
        report: ValidationReport,
        outputPath: Path,
    ) {
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, json.encodeToString(report))
    }

    /**
     * Draw JSON boxes onto a copy of the original source image for visual QA.
     */
    fun writeOverlayOnSource(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
        outputPath: Path,
    ) {
        val canvas = sourceBgr.clone()
        for (box in boxes) {
            val color =
                if (box.isRoot) {
                    Scalar(0.0, 165.0, 255.0)
                } else {
                    Scalar(0.0, 200.0, 0.0)
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
        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }

    private fun checkCardBox(
        bgr: Mat,
        hsv: Mat,
        box: DetectedBox,
    ): BoxCheck {
        val notes = mutableListOf<String>()
        val midY = box.centerY.coerceIn(0, bgr.rows() - 1)
        val midX = box.centerX.coerceIn(0, bgr.cols() - 1)

        // Strip just inside left edge (gender bar)
        val leftGender =
            sampleRatio(box.x + 1, box.y + 2, maxOf(3, box.width / 20), box.height - 4, bgr) { x, y ->
                isGenderBar(hsv, x, y)
            }

        // Interior away from bar
        val interiorX = (box.x + box.width / 3).coerceIn(0, bgr.cols() - 1)
        val interior =
            sampleRatio(interiorX, box.y + 2, maxOf(8, box.width / 3), box.height - 4, bgr) { x, y ->
                isCardBody(hsv, x, y) || isInk(bgr, x, y) || isGenderBar(hsv, x, y)
            }

        val probe = 4
        val outsideRight =
            sampleRatio(box.xEnd + 2, midY - box.height / 4, probe, box.height / 2, bgr) { x, y ->
                isOutsideCard(bgr, hsv, x, y)
            }
        val outsideLeft =
            sampleRatio(box.x - probe - 1, midY - box.height / 4, probe, box.height / 2, bgr) { x, y ->
                isOutsideCard(bgr, hsv, x, y)
            }
        val outsideTop =
            sampleRatio(midX - box.width / 4, box.y - probe - 1, box.width / 2, probe, bgr) { x, y ->
                isOutsideCard(bgr, hsv, x, y)
            }
        val outsideBottom =
            sampleRatio(midX - box.width / 4, box.yEnd + 2, box.width / 2, probe, bgr) { x, y ->
                isOutsideCard(bgr, hsv, x, y)
            }

        if (leftGender < 0.25) notes += "left edge weak gender-bar ratio=$leftGender"
        if (interior < 0.55) notes += "interior weak card ratio=$interior"
        if (outsideRight < 0.85) notes += "right outside still on card ratio=$outsideRight"
        if (outsideLeft < 0.85) notes += "left outside still on card ratio=$outsideLeft"
        if (outsideTop < 0.70) notes += "top outside still on card ratio=$outsideTop"
        if (outsideBottom < 0.70) notes += "bottom outside still on card ratio=$outsideBottom"

        val passed =
            leftGender >= 0.25 &&
                interior >= 0.55 &&
                outsideRight >= 0.85 &&
                outsideLeft >= 0.85 &&
                outsideTop >= 0.70 &&
                outsideBottom >= 0.70

        return BoxCheck(
            id = box.id,
            passed = passed,
            leftGenderBar = leftGender,
            interiorCard = interior,
            outsideRightWhite = outsideRight,
            outsideLeftWhite = outsideLeft,
            outsideTopWhite = outsideTop,
            outsideBottomWhite = outsideBottom,
            notes = notes,
        )
    }

    private fun sampleRatio(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        bgr: Mat,
        predicate: (Int, Int) -> Boolean,
    ): Double {
        if (w <= 0 || h <= 0) return 0.0
        val x0 = x.coerceIn(0, bgr.cols() - 1)
        val y0 = y.coerceIn(0, bgr.rows() - 1)
        val x1 = (x + w - 1).coerceIn(0, bgr.cols() - 1)
        val y1 = (y + h - 1).coerceIn(0, bgr.rows() - 1)
        if (x1 < x0 || y1 < y0) return 0.0

        var hit = 0
        var total = 0
        var yy = y0
        while (yy <= y1) {
            var xx = x0
            while (xx <= x1) {
                if (predicate(xx, yy)) hit++
                total++
                xx++
            }
            yy++
        }
        return if (total == 0) 0.0 else hit.toDouble() / total
    }

    private fun isWhite(
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val px = hsv.get(y, x)
        return px[1] < 8.0 && px[2] > 245.0
    }

    /** White page, watermark, or connector ink — but not another card body/bar. */
    private fun isOutsideCard(
        bgr: Mat,
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean = !isCardBody(hsv, x, y) && !isGenderBar(hsv, x, y)

    private fun isCardBody(
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val px = hsv.get(y, x)
        return px[0] in 85.0..125.0 && px[1] in 5.0..55.0 && px[2] >= 200.0
    }

    private fun isGenderBar(
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val px = hsv.get(y, x)
        val hue = px[0]
        val sat = px[1]
        val value = px[2]
        val blue = hue in 95.0..135.0 && sat >= 50.0 && value >= 40.0
        val pink = (hue >= 145.0 || hue <= 20.0) && sat >= 40.0 && value >= 40.0
        return blue || pink
    }

    private fun isInk(
        bgr: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val px = bgr.get(y, x)
        return (px[0] + px[1] + px[2]) / 3.0 < 130.0
    }
}
