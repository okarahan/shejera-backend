package com.shejera.importing.cv

/** Intermediate geometry from [ShapeDetector] before conversion to step-1 [com.shejera.importing.cv.steps.DetectedBox]. */
data class DetectedCard(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val isRootCircle: Boolean,
    val sexFromColor: String? = null,
) {
    val cx: Double get() = x + width / 2.0
    val cy: Double get() = y + height / 2.0
}

data class ParsedCardText(
    val givenName: String? = null,
    val surname: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val birthPlace: String? = null,
    val role: String? = null,
)
