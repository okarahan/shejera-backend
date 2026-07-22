package com.shejera.importing.cv.steps

/**
 * Cleans the first-line OCR result into a display name.
 * Drops standalone dashes (missing surname marker) and OCR noise.
 */
object NameLineCleaner {
    fun clean(raw: String): String? {
        val firstLine =
            raw
                .lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return null

        val tokens =
            firstLine
                .replace('|', 'I')
                .replace(Regex("""[©®™]+"""), " ")
                .replace(Regex("""[_\.=:;,'‘’"“”]+"""), " ")
                .split(Regex("""\s+"""))
                .map { it.trim().trim('-') }
                .filter { token ->
                    token.isNotEmpty() &&
                        token != "-" &&
                        token.any { it.isLetter() }
                }

        val cleaned = tokens.joinToString(" ").trim()
        if (cleaned.count { it.isLetter() } < 2) return null
        return cleaned
    }
}
