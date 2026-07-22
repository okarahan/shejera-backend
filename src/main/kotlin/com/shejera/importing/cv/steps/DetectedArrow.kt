package com.shejera.importing.cv.steps

import kotlinx.serialization.Serializable

/**
 * One connector arrow from step 2 (geometry only — no box linking yet).
 *
 * [startX]/[startY] = where the shaft begins (toward the parent box).
 * [tipX]/[tipY] = arrowhead (points into the child box from above).
 *
 * The bent path between start and tip is intentionally not stored.
 */
@Serializable
data class DetectedArrow(
    val id: Int,
    val startX: Int,
    val startY: Int,
    val tipX: Int,
    val tipY: Int,
    /** Euclidean length start→tip (straight), for QA. */
    val straightLength: Double,
)

@Serializable
data class ArrowDetectionResult(
    val sourceFile: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val arrowCount: Int,
    val arrows: List<DetectedArrow>,
)
