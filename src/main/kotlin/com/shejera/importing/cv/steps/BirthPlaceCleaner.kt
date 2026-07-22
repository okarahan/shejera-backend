package com.shejera.importing.cv.steps

import com.shejera.importing.cv.CardTextParser

/**
 * Cleans OCR of the last card line: `Doğum Yeri:X` (no space required after ":").
 * Everything after the label is the birth place (one or more words).
 */
object BirthPlaceCleaner {
    private val labelPrefix =
        Regex("""(?iu)^.*?(?:do[gğ]um\s*yer[iı]?|[jdg]?um\s*yer[iı]?)\s*[:.\-]?""")

    fun clean(raw: String): String? {
        val line =
            raw
                .lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return null

        val fromParser = CardTextParser.parseBirthPlaceLine(line)
        val candidate =
            fromParser
                ?: line
                    .replace(labelPrefix, "")
                    .trim()
                    .trimStart(':', '.', '-', ' ')

        // If label stripping failed partially, peel again
        val value =
            if (candidate.contains(Regex("""(?iu)do[gğ]um|yer[iı]?"""))) {
                candidate.replace(labelPrefix, "").trim().trimStart(':', '.', '-', ' ')
            } else {
                candidate
            }

        if (value.isEmpty() || value == "-") return null
        if (value.count { it.isLetter() } < 2) return null
        return value
    }
}
