package com.shejera.importing.cv.steps

import com.shejera.importing.cv.CardTextParser

/**
 * Cleans OCR of the DT/ÖT line and parses birth/death dates.
 *
 * Format: `DT:D.M.YYYY ÖT:DD.MM.YYYY` — day/month may be 1 or 2 digits;
 * `-` means missing. Output dates are zero-padded `DD.MM.YYYY` when full.
 */
object DateLineCleaner {
    data class Dates(
        val birthDate: String?,
        val deathDate: String?,
    )

    fun clean(raw: String): Dates {
        val line =
            raw
                .lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return Dates(null, null)

        val normalized =
            line
                // "29 .12.1942" / "22 8.1954" → proper dotted date
                .replace(Regex("""\s*\.\s*"""), ".")
                .replace(Regex("""(\d)\s+(\d)"""), "$1.$2")

        val (birth, death) = CardTextParser.parseDtOtLine(normalized)
        return Dates(birthDate = birth, deathDate = death)
    }
}