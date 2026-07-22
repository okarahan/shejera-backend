package com.shejera.importing.cv.steps

import org.opencv.core.Mat
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extracts arrows as (start, tip) pairs from an arrow-ink binary mask.
 *
 * Tip = arrowhead (on / just above the child box).
 * Start = farthest ink reachable above the tip (shaft origin toward parent).
 *
 * Box tops from step 1 seed tip locations only — no parentId (step 3).
 */
object ArrowGeometryDetector {
    private val log = LoggerFactory.getLogger(ArrowGeometryDetector::class.java)

    private data class Pix(val x: Int, val y: Int)

    fun detect(
        arrowInkMask: Mat,
        boxes: List<DetectedBox>,
    ): List<DetectedArrow> {
        val arrows = mutableListOf<DetectedArrow>()
        val usedTips = mutableSetOf<Pix>()

        for (box in boxes.filter { !it.isRoot }.sortedWith(compareBy({ it.y }, { it.x }))) {
            val tip =
                findTipForBox(arrowInkMask, box)
                    ?: continue
            if (usedTips.any { hypot((it.x - tip.x).toDouble(), (it.y - tip.y).toDouble()) < 12 }) {
                continue
            }

            val start = findStartAbove(arrowInkMask, tip) ?: continue
            val len = hypot((start.x - tip.x).toDouble(), (start.y - tip.y).toDouble())
            if (len < 12) continue

            usedTips += tip
            arrows +=
                DetectedArrow(
                    id = arrows.size + 1,
                    startX = start.x,
                    startY = start.y,
                    tipX = tip.x,
                    tipY = tip.y,
                    straightLength = len,
                )
        }

        log.info("[cv-step2] arrows detected={}", arrows.size)
        return arrows
    }

    private fun findTipForBox(
        mask: Mat,
        box: DetectedBox,
    ): Pix? {
        val bandH = max(14, (box.height * 0.3).roundToInt())
        val y0 = (box.y - bandH).coerceAtLeast(0)
        val y1 = (box.y + 6).coerceAtMost(mask.rows() - 1)
        val x0 = (box.centerX - box.width * 0.45).toInt().coerceAtLeast(0)
        val x1 = (box.centerX + box.width * 0.45).toInt().coerceAtMost(mask.cols() - 1)

        // Prefer the lowest ink pixel near the top-center (true tip)
        var best: Pix? = null
        var bestScore = Int.MIN_VALUE
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (mask.get(y, x)[0] < 128.0) continue
                val score = y * 1000 - kotlin.math.abs(x - box.centerX)
                if (score > bestScore) {
                    bestScore = score
                    best = Pix(x, y)
                }
            }
        }
        return best
    }

    /**
     * BFS from tip over ink; do not go below the tip. Start = farthest pixel reached.
     */
    private fun findStartAbove(
        mask: Mat,
        tip: Pix,
    ): Pix? {
        val cols = mask.cols()
        val rows = mask.rows()
        val visited = BooleanArray(rows * cols)
        val queue = ArrayDeque<Pix>()
        queue.add(tip)
        visited[tip.y * cols + tip.x] = true

        var best = tip
        var bestDist = 0.0
        var steps = 0

        while (queue.isNotEmpty() && steps < 400_000) {
            val p = queue.removeFirst()
            steps++
            val dist = hypot((p.x - tip.x).toDouble(), (p.y - tip.y).toDouble())
            // Prefer points above the tip
            val score = dist + if (p.y < tip.y) (tip.y - p.y) * 1.5 else 0.0
            if (score > bestDist && p != tip) {
                bestDist = score
                best = p
            }

            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = p.x + dx
                    val ny = p.y + dy
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
                    if (ny > tip.y + 2) continue
                    val idx = ny * cols + nx
                    if (visited[idx]) continue
                    if (mask.get(ny, nx)[0] < 128.0) continue
                    visited[idx] = true
                    queue.add(Pix(nx, ny))
                }
            }
        }

        return if (best != tip && bestDist >= 12) best else null
    }
}
