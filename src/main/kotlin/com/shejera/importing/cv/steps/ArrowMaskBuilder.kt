package com.shejera.importing.cv.steps

import com.shejera.importing.cv.ImagePreprocessor
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds an image/mask that keeps black connector arrows and removes:
 * - watermarks
 * - person-box fills (text / gender bars)
 *
 * Tip band at the top of non-root boxes is kept so arrowheads stay visible.
 */
object ArrowMaskBuilder {
    /**
     * White canvas with only dark arrow strokes (for visual QA).
     */
    fun buildArrowsOnlyBgr(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
    ): Mat {
        val mask = buildArrowInkMask(sourceBgr, boxes)
        val out = Mat(sourceBgr.size(), sourceBgr.type(), Scalar(255.0, 255.0, 255.0))
        out.setTo(Scalar(0.0, 0.0, 0.0), mask)
        return out
    }

    /**
     * Binary mask: 255 = arrow ink, 0 = background.
     */
    fun buildArrowInkMask(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
    ): Mat {
        val cleaned = ImagePreprocessor.suppressGrayWatermark(sourceBgr)
        val withoutBoxes = cleaned.clone()
        for (box in boxes) {
            // Keep a thin top band on non-root cards so the tip remains
            fillBox(withoutBoxes, box, Scalar(255.0, 255.0, 255.0), keepTipBand = !box.isRoot)
        }

        val mask = darkLineMask(withoutBoxes)
        removeBulkyComponents(mask)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        // Bridge tiny gaps between shaft and tip after box erase
        Imgproc.dilate(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        return mask
    }

    private fun fillBox(
        bgr: Mat,
        box: DetectedBox,
        color: Scalar,
        keepTipBand: Boolean,
    ) {
        val insetX = max(1, (box.width * 0.03).roundToInt())
        val tipKeep =
            if (keepTipBand) {
                max(8, (box.height * 0.16).roundToInt())
            } else {
                0
            }
        val x = (box.x + insetX).coerceIn(0, bgr.cols() - 1)
        val y = (box.y + tipKeep).coerceIn(0, bgr.rows() - 1)
        val w = (box.width - 2 * insetX).coerceAtLeast(1).coerceAtMost(bgr.cols() - x)
        val h = (box.yEnd - y + 1).coerceAtLeast(1).coerceAtMost(bgr.rows() - y)
        if (w > 0 && h > 0) {
            Imgproc.rectangle(bgr, Rect(x, y, w, h), color, Imgproc.FILLED)
        }
    }

    private fun darkLineMask(bgr: Mat): Mat {
        val gray = ImagePreprocessor.toGray(bgr)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 55.0, 255.0, Imgproc.THRESH_BINARY_INV)
        return binary
    }

    private fun removeBulkyComponents(binary: Mat) {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary.clone(), contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = binary.rows() * binary.cols().toDouble()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            val rect = Imgproc.boundingRect(contour)
            val thick =
                area > 2_500 &&
                    rect.width > 40 &&
                    rect.height > 40 &&
                    area / imageArea > 0.0002
            val notLineLike =
                rect.width > 30 &&
                    rect.height > 30 &&
                    area > 0.35 * rect.width * rect.height
            if (thick || notLineLike) {
                Imgproc.drawContours(binary, listOf(contour), 0, Scalar(0.0), Imgproc.FILLED)
            }
        }
    }
}
