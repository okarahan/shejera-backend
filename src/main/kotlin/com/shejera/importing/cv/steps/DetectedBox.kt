package com.shejera.importing.cv.steps

import kotlinx.serialization.Serializable

/**
 * Axis-aligned person box from step 1 (geometry only — no OCR / linking).
 *
 * Coordinates are in source-image pixels, origin top-left.
 * [x], [y] = top-left; [xEnd], [yEnd] = bottom-right inclusive.
 *
 * [id] reading order: root = 0, then rows top→bottom, left→right within each row.
 */
@Serializable
data class DetectedBox(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val xEnd: Int,
    val yEnd: Int,
    val centerX: Int,
    val centerY: Int,
    /** True when this is the yellow-highlighted root person. */
    val isRoot: Boolean = false,
)

@Serializable
data class BoxDetectionResult(
    val sourceFile: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val boxCount: Int,
    val boxes: List<DetectedBox>,
)
