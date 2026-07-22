package com.shejera.importing.cv

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

/**
 * Detects person cards on Turkish Nüfus / Alt-Üst-Soy charts.
 *
 * Primary strategy: find the narrow blue/pink gender bars on the left of each
 * card, then expand right to recover the full card box. Canny rectangles are
 * only a fallback (they fail on these soft-border documents).
 */
object ShapeDetector {
    private val log = LoggerFactory.getLogger(ShapeDetector::class.java)

    fun detectCards(bgr: Mat): List<DetectedCard> {
        val hsv = ImagePreprocessor.toHsv(bgr)
        log.info("[cv] detect yellow root circle…")
        val root = detectYellowCircle(bgr, hsv)
        if (root == null) {
            log.warn("[cv] no yellow root circle found")
        } else {
            log.info(
                "[cv] root circle at {}x{} size {}x{} sex={}",
                root.x,
                root.y,
                root.width,
                root.height,
                root.sexFromColor,
            )
        }

        log.info("[cv] detect cards via gender bars…")
        val fromBars = detectCardsFromGenderBars(bgr, hsv, root)
        log.info("[cv] gender-bar cards found={}", fromBars.size)

        val rects =
            if (fromBars.size >= 2) {
                fromBars
            } else {
                log.warn("[cv] gender-bar detection weak; falling back to Canny rectangles")
                val canny = detectRectanglesCanny(bgr, root)
                log.info("[cv] canny rectangles found={}", canny.size)
                mergeUnique(fromBars, canny)
            }

        return buildList {
            if (root != null) add(root)
            addAll(rects.filter { root == null || !overlaps(root, it.x, it.y, it.width, it.height) })
        }
    }

    private fun detectYellowCircle(
        bgr: Mat,
        hsv: Mat,
    ): DetectedCard? {
        val mask = Mat()
        Core.inRange(hsv, Scalar(18.0, 80.0, 80.0), Scalar(40.0, 255.0, 255.0), mask)
        Imgproc.GaussianBlur(mask, mask, Size(9.0, 9.0), 0.0)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imageArea = bgr.rows() * bgr.cols().toDouble()
        val candidates =
            contours.mapNotNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < imageArea * 0.0004 || area > imageArea * 0.25) return@mapNotNull null
                val rect = Imgproc.boundingRect(contour)
                val ratio = rect.width.toDouble() / max(rect.height, 1)
                if (ratio !in 0.55..1.6) return@mapNotNull null
                val peri = perimeter(contour)
                if (peri <= 0.0) return@mapNotNull null
                val circularity = 4 * Math.PI * area / (peri * peri)
                // Yellow is often a ring highlight → lower circularity than a filled disk
                if (circularity < 0.25) return@mapNotNull null
                rect to area
            }

        val best = candidates.maxByOrNull { it.second }?.first ?: return null
        val sex = GenderBarDetector.detect(bgr, best.x, best.y, best.width, best.height)
        return DetectedCard(
            id = "root",
            x = best.x,
            y = best.y,
            width = best.width,
            height = best.height,
            isRootCircle = true,
            sexFromColor = sex,
        )
    }

    private fun detectCardsFromGenderBars(
        bgr: Mat,
        hsv: Mat,
        root: DetectedCard?,
    ): List<DetectedCard> {
        val genderMask = buildGenderBarMask(hsv)
        // Strengthen thin vertical bars
        val kernel =
            Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(3.0, max(9.0, bgr.rows() * 0.01)),
            )
        Imgproc.morphologyEx(genderMask, genderMask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.dilate(genderMask, genderMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 3.0)))

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(genderMask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        log.info("[cv] gender-bar contours={}", contours.size)

        val imageH = bgr.rows()
        val imageW = bgr.cols()
        val minBarH = max(24, (imageH * 0.035).toInt())
        val maxBarH = (imageH * 0.55).toInt()
        val maxBarW = max(28, (imageW * 0.015).toInt())

        val cards = mutableListOf<DetectedCard>()
        var rejected = 0
        var index = 0

        for (contour in contours) {
            val bar = Imgproc.boundingRect(contour)
            val aspect = bar.height.toDouble() / max(bar.width, 1)
            if (bar.height < minBarH || bar.height > maxBarH) {
                rejected++
                continue
            }
            if (bar.width > maxBarW || aspect < 2.2) {
                rejected++
                continue
            }

            val sex =
                GenderBarDetector.detect(bgr, bar.x, bar.y, max(bar.width * 2, 8), bar.height)
                    ?: inferSexFromMask(hsv, bar)

            val cardRect = expandCardFromBar(bgr, hsv, bar)
            if (cardRect.width < bar.width * 4 || cardRect.height < minBarH) {
                rejected++
                continue
            }
            if (root != null && overlaps(root, cardRect.x, cardRect.y, cardRect.width, cardRect.height)) {
                continue
            }
            if (cards.any { overlaps(it, cardRect.x, cardRect.y, cardRect.width, cardRect.height) }) {
                continue
            }

            cards +=
                DetectedCard(
                    id = "c${index++}",
                    x = cardRect.x,
                    y = cardRect.y,
                    width = cardRect.width,
                    height = cardRect.height,
                    isRootCircle = false,
                    sexFromColor = sex,
                )
        }

        log.info("[cv] gender-bar rejected={} accepted={}", rejected, cards.size)
        return cards.sortedWith(compareBy<DetectedCard> { it.y }.thenBy { it.x })
    }

    private fun buildGenderBarMask(hsv: Mat): Mat {
        val blue = Mat()
        val pink = Mat()
        val pinkLow = Mat()
        Core.inRange(hsv, Scalar(95.0, 50.0, 40.0), Scalar(140.0, 255.0, 255.0), blue)
        Core.inRange(hsv, Scalar(140.0, 30.0, 40.0), Scalar(179.0, 255.0, 255.0), pink)
        Core.inRange(hsv, Scalar(0.0, 30.0, 40.0), Scalar(15.0, 255.0, 255.0), pinkLow)
        Core.bitwise_or(pink, pinkLow, pink)
        val out = Mat()
        Core.bitwise_or(blue, pink, out)
        return out
    }

    private fun inferSexFromMask(
        hsv: Mat,
        bar: Rect,
    ): String? {
        val safe =
            Rect(
                bar.x.coerceIn(0, hsv.cols() - 1),
                bar.y.coerceIn(0, hsv.rows() - 1),
                bar.width.coerceAtMost(hsv.cols() - bar.x).coerceAtLeast(1),
                bar.height.coerceAtMost(hsv.rows() - bar.y).coerceAtLeast(1),
            )
        val roi = Mat(hsv, safe)
        val blue = Mat()
        val pink = Mat()
        Core.inRange(roi, Scalar(95.0, 50.0, 40.0), Scalar(140.0, 255.0, 255.0), blue)
        Core.inRange(roi, Scalar(140.0, 30.0, 40.0), Scalar(179.0, 255.0, 255.0), pink)
        val pinkLow = Mat()
        Core.inRange(roi, Scalar(0.0, 30.0, 40.0), Scalar(15.0, 255.0, 255.0), pinkLow)
        Core.bitwise_or(pink, pinkLow, pink)
        val b = Core.countNonZero(blue)
        val p = Core.countNonZero(pink)
        return when {
            b > p && b > 10 -> "M"
            p > b && p > 10 -> "F"
            else -> null
        }
    }

    /**
     * Expand from the gender bar across the pale cyan card body.
     * The box ends as soon as pure white background is reached (no min-width inflate).
     */
    private fun expandCardFromBar(
        bgr: Mat,
        hsv: Mat,
        bar: Rect,
    ): Rect {
        val probeYs =
            listOf(
                bar.y + (bar.height * 0.25).toInt(),
                bar.y + (bar.height * 0.50).toInt(),
                bar.y + (bar.height * 0.75).toInt(),
            ).map { it.coerceIn(0, bgr.rows() - 1) }

        val padXLeft = max(1, (bar.width * 0.2).toInt())
        val left = (bar.x - padXLeft).coerceAtLeast(0)
        val maxX = min(bgr.cols() - 1, bar.x + max(bar.height * 4, bar.width * 40))

        var endX = (bar.x + bar.width).coerceAtMost(bgr.cols() - 1)
        var whiteRun = 0
        var x = bar.x
        while (x <= maxX) {
            var whiteVotes = 0
            var cardVotes = 0
            for (y in probeYs) {
                when {
                    isWhiteBackground(hsv, x, y) -> whiteVotes++
                    isOnCardSurface(bgr, hsv, x, y) -> cardVotes++
                }
            }
            if (whiteVotes >= 2) {
                whiteRun++
                if (whiteRun >= 2) break
            } else {
                whiteRun = 0
                if (cardVotes >= 1) {
                    endX = x
                }
            }
            x++
        }

        // Vertical bounds: stay on card body; stop at white above/below
        val midX =
            ((bar.x + bar.width + endX) / 2)
                .coerceIn(0, bgr.cols() - 1)
        var top = bar.y
        var bottom = bar.y + bar.height - 1
        var run = 0
        var yUp = bar.y - 1
        while (yUp >= 0 && bar.y - yUp <= bar.height) {
            if (isWhiteBackground(hsv, midX, yUp)) {
                run++
                if (run >= 2) break
            } else if (isOnCardSurface(bgr, hsv, midX, yUp)) {
                run = 0
                top = yUp
            } else {
                run++
                if (run >= 2) break
            }
            yUp--
        }
        run = 0
        var yDown = bar.y + bar.height
        while (yDown < bgr.rows() && yDown - (bar.y + bar.height) <= bar.height) {
            if (isWhiteBackground(hsv, midX, yDown)) {
                run++
                if (run >= 2) break
            } else if (isOnCardSurface(bgr, hsv, midX, yDown)) {
                run = 0
                bottom = yDown
            } else {
                run++
                if (run >= 2) break
            }
            yDown++
        }

        val width = (endX - left + 1).coerceAtLeast(bar.width + 1)
        val height = (bottom - top + 1).coerceAtLeast(bar.height)
        return Rect(left, top, width, height)
    }

    /** True page background (not the pale cyan card fill). */
    private fun isWhiteBackground(
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val px = hsv.get(y, x)
        return px[1] < 8.0 && px[2] > 245.0
    }

    /**
     * Gender bar, pale cyan card fill, or dark ink sitting on the card.
     */
    private fun isOnCardSurface(
        bgr: Mat,
        hsv: Mat,
        x: Int,
        y: Int,
    ): Boolean {
        val h = hsv.get(y, x)
        val hue = h[0]
        val sat = h[1]
        val value = h[2]

        // Pink / blue gender bar
        val genderBlue = hue in 95.0..135.0 && sat >= 50.0 && value >= 40.0
        val genderPink = (hue >= 145.0 || hue <= 20.0) && sat >= 40.0 && value >= 40.0
        if (genderBlue || genderPink) return true

        // Pale cyan card body (distinct from pure white)
        if (hue in 85.0..125.0 && sat in 5.0..55.0 && value >= 200.0) return true

        val bgrPx = bgr.get(y, x)
        val lum = (bgrPx[0] + bgrPx[1] + bgrPx[2]) / 3.0
        // Dark text / icons on the card
        if (lum < 130.0) return true

        return false
    }

    private fun detectRectanglesCanny(
        bgr: Mat,
        root: DetectedCard?,
    ): List<DetectedCard> {
        val gray = ImagePreprocessor.toGray(bgr)
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blur, edges, 40.0, 120.0)
        val dilated = Mat()
        Imgproc.dilate(
            edges,
            dilated,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(dilated, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imageArea = bgr.rows() * bgr.cols().toDouble()
        // Wide Nüfus charts have many small cards — keep min area low
        val minArea = imageArea * 0.00025
        val maxArea = imageArea * 0.08

        val rects = mutableListOf<DetectedCard>()
        var index = 0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx2f = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx2f, 0.04 * peri, true)
            if (approx2f.total() !in 4..10) continue

            val rect = Imgproc.boundingRect(contour)
            val ratio = rect.width.toDouble() / max(rect.height, 1)
            if (ratio !in 0.45..2.8) continue
            if (root != null && overlaps(root, rect.x, rect.y, rect.width, rect.height)) continue
            if (rects.any { overlaps(it, rect.x, rect.y, rect.width, rect.height) }) continue

            val sex = GenderBarDetector.detect(bgr, rect.x, rect.y, rect.width, rect.height)
            rects +=
                DetectedCard(
                    id = "c${index++}",
                    x = rect.x,
                    y = rect.y,
                    width = rect.width,
                    height = rect.height,
                    isRootCircle = false,
                    sexFromColor = sex,
                )
        }

        return rects.sortedWith(compareBy<DetectedCard> { it.y }.thenBy { it.x })
    }

    private fun mergeUnique(
        primary: List<DetectedCard>,
        secondary: List<DetectedCard>,
    ): List<DetectedCard> {
        val out = primary.toMutableList()
        var index = out.size
        for (card in secondary) {
            if (out.any { overlaps(it, card.x, card.y, card.width, card.height) }) continue
            out += card.copy(id = "c${index++}")
        }
        return out.sortedWith(compareBy<DetectedCard> { it.y }.thenBy { it.x })
    }

    private fun perimeter(contour: MatOfPoint): Double =
        Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)

    private fun overlaps(
        card: DetectedCard,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): Boolean {
        val ix1 = max(card.x, x)
        val iy1 = max(card.y, y)
        val ix2 = min(card.x + card.width, x + w)
        val iy2 = min(card.y + card.height, y + h)
        if (ix2 <= ix1 || iy2 <= iy1) return false
        val intersection = (ix2 - ix1) * (iy2 - iy1)
        val smaller = min(card.width * card.height, w * h).toDouble()
        return intersection / smaller > 0.45
    }
}
