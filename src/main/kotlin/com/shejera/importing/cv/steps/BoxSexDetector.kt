package com.shejera.importing.cv.steps

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * Reads the narrow gender color strip on the left edge of a box.
 * Blue → "M", Pink → "F".
 */
object BoxSexDetector {
    private val log = LoggerFactory.getLogger(BoxSexDetector::class.java)

    /** Sample width of the left strip in pixels (bar is thin). */
    private const val STRIP_PX = 4

    fun detectAll(
        bgr: Mat,
        boxes: List<DetectedBox>,
    ): Map<Int, String?> {
        val out = LinkedHashMap<Int, String?>()
        for (box in boxes) {
            val sex =
                if (box.isRoot) {
                    // Yellow root circle has no gender bar
                    null
                } else {
                    detect(bgr, box)
                }
            out[box.id] = sex
            log.info("[cv-step4] box {} sex={}", box.id, sex)
        }
        return out
    }

    fun detect(
        bgr: Mat,
        box: DetectedBox,
    ): String? {
        val stripW = STRIP_PX.coerceAtMost(box.width / 4).coerceAtLeast(2)
        // Inset 1px from outer edge to avoid anti-alias / white fringe
        val x = (box.x + 1).coerceIn(0, bgr.cols() - 1)
        val y = (box.y + 2).coerceIn(0, bgr.rows() - 1)
        val w = stripW.coerceAtMost(bgr.cols() - x)
        val h = (box.height - 4).coerceAtLeast(1).coerceAtMost(bgr.rows() - y)
        if (w <= 0 || h <= 0) return null

        val roi = Mat(bgr, Rect(x, y, w, h))
        val hsv = Mat()
        Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV)

        val blue = Mat()
        val pink = Mat()
        val pinkLow = Mat()
        Core.inRange(hsv, Scalar(95.0, 50.0, 40.0), Scalar(135.0, 255.0, 255.0), blue)
        Core.inRange(hsv, Scalar(140.0, 30.0, 40.0), Scalar(179.0, 255.0, 255.0), pink)
        Core.inRange(hsv, Scalar(0.0, 30.0, 40.0), Scalar(15.0, 255.0, 255.0), pinkLow)
        Core.bitwise_or(pink, pinkLow, pink)

        val blueCount = Core.countNonZero(blue)
        val pinkCount = Core.countNonZero(pink)
        val total = max(1, w * h)
        val blueRatio = blueCount.toDouble() / total
        val pinkRatio = pinkCount.toDouble() / total

        return when {
            blueRatio > 0.15 && blueRatio >= pinkRatio -> "M"
            pinkRatio > 0.15 && pinkRatio > blueRatio -> "F"
            else -> null
        }
    }
}
