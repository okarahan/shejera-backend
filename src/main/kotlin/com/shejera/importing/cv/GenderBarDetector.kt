package com.shejera.importing.cv

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Detects the narrow colored gender bar on the left of person cards.
 * Blue → M, Pink → F.
 */
object GenderBarDetector {
    fun detect(
        bgr: Mat,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): String? {
        if (width < 10 || height < 10) return null
        val barWidth = maxOf(4, (width * 0.08).toInt())
        val safeX = x.coerceIn(0, bgr.cols() - 1)
        val safeY = y.coerceIn(0, bgr.rows() - 1)
        val safeW = barWidth.coerceAtMost(bgr.cols() - safeX)
        val safeH = height.coerceAtMost(bgr.rows() - safeY)
        if (safeW <= 0 || safeH <= 0) return null

        val roi = Mat(bgr, Rect(safeX, safeY, safeW, safeH))
        val hsv = Mat()
        Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV)

        val blueMask = Mat()
        val pinkMask = Mat()
        // OpenCV Hue is 0..180
        Core.inRange(hsv, Scalar(95.0, 60.0, 50.0), Scalar(135.0, 255.0, 255.0), blueMask)
        Core.inRange(hsv, Scalar(140.0, 40.0, 50.0), Scalar(179.0, 255.0, 255.0), pinkMask)
        val pinkLow = Mat()
        Core.inRange(hsv, Scalar(0.0, 40.0, 50.0), Scalar(12.0, 255.0, 255.0), pinkLow)
        Core.bitwise_or(pinkMask, pinkLow, pinkMask)

        val blueCount = Core.countNonZero(blueMask)
        val pinkCount = Core.countNonZero(pinkMask)
        val total = safeW * safeH
        if (total == 0) return null

        val blueRatio = blueCount.toDouble() / total
        val pinkRatio = pinkCount.toDouble() / total

        return when {
            blueRatio > 0.18 && blueRatio >= pinkRatio -> "M"
            pinkRatio > 0.18 && pinkRatio > blueRatio -> "F"
            else -> null
        }
    }
}
