package com.shejera.importing.cv

/**
 * Parses OCR text from Nüfus-style person cards.
 *
 * Layout:
 *   1. Name line — given + surname ("-" = missing surname part)
 *   2. Relationship — one or more lines (long labels may wrap)
 *   3. Spacing
 *   4. Date line — "DT …" and "ÖT …" on the same line
 *   5. Place line — "Doğum Yeri …"
 *
 * Tolerates common OCR noise: missing leading letters on labels,
 * "OT"/" )T" for "ÖT"/"DT", ":" instead of ".", single-digit day/month.
 */
object CardTextParser {
    private val dateValuePattern = """(\d{1,2}\.\d{1,2}\.\d{4}|\d{4})"""
    private val dateValueRegex = Regex(dateValuePattern)
    private val dateOrDashPattern = """($dateValuePattern|-)"""

    // (?iu) = case-insensitive + Unicode (needed for Ö/İ). Avoid \b — breaks on Turkish letters.
    private val dtFieldRegex =
        Regex("""(?iu)(?:^|[^A-Za-z0-9])(?:dt|\)t)\.?\s*[:\-]?\s*$dateOrDashPattern""")
    private val otFieldRegex =
        Regex("""(?iu)(?:^|[^A-Za-z0-9])(?:öt|ot|ö\s*t)\.?\s*[:\-]?\s*$dateOrDashPattern""")

    /** "Doğum Yeri", OCR fragments like "jum Yeri", "um Yeri", "Doğum Yer", … */
    private val placeFieldRegex =
        Regex(
            """(?iu)(?:do[gğ]um\s*yer[iı]?|[jdg]?um\s*yer[iı]?)\s*[:.\-]?\s*(.+)$""",
        )

    fun parse(rawText: String): ParsedCardText {
        val lines =
            rawText
                .lines()
                .map { cleanupOcrNoise(it) }
                .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return ParsedCardText()
        }

        val nameLine = lines.first()
        var index = 1

        val roleParts = mutableListOf<String>()
        while (index < lines.size && !isDateLine(lines[index]) && !isPlaceLine(lines[index])) {
            roleParts += lines[index]
            index++
        }
        val role = roleParts.joinToString(" ").ifBlank { null }

        var birthDate: String? = null
        var deathDate: String? = null
        if (index < lines.size && isDateLine(lines[index])) {
            val parsedDates = parseDateLine(lines[index])
            birthDate = parsedDates.first
            deathDate = parsedDates.second
            index++
        }

        var birthPlace: String? = null
        if (index < lines.size) {
            birthPlace = parsePlaceLine(lines[index])
        }

        val (given, surname) = splitName(nameLine)
        return ParsedCardText(
            givenName = given,
            surname = surname,
            birthDate = birthDate,
            deathDate = deathDate,
            birthPlace = birthPlace,
            role = role,
        )
    }

    /** Sex comes from the colored gender bar, not from relationship wording. */
    fun resolveSex(
        @Suppress("UNUSED_PARAMETER") role: String?,
        sexFromColor: String?,
    ): String? = sexFromColor

    private fun isDateLine(line: String): Boolean {
        if (dtFieldRegex.containsMatchIn(line) || otFieldRegex.containsMatchIn(line)) return true
        // OCR sometimes drops markers but keeps "6.4.1960 ÖT:-" / ")T:6.4.1960"
        return dateValueRegex.containsMatchIn(line) &&
            line.contains(Regex("""(?iu)(?:öt|ot|dt|\)t)"""))
    }

    private fun isPlaceLine(line: String): Boolean =
        placeFieldRegex.containsMatchIn(line)

    /**
     * Parse a DT/ÖT line. Day/month may be 1 or 2 digits; "-" means missing.
     * Returns normalized dates as `DD.MM.YYYY` (zero-padded) or year-only / null.
     */
    fun parseDtOtLine(line: String): Pair<String?, String?> = parseDateLine(cleanupOcrNoise(line))

    private fun parseDateLine(line: String): Pair<String?, String?> {
        val dt = dtFieldRegex.find(line)?.groupValues?.getOrNull(1)
        val ot = otFieldRegex.find(line)?.groupValues?.getOrNull(1)

        val birth =
            normalizeDate(dt)
                ?: run {
                    // ")T:6.4.1960" or bare first date before ÖT
                    val all = dateValueRegex.findAll(line).map { it.value }.toList()
                    all.firstOrNull()?.let { normalizeDate(it) }
                }

        val death =
            normalizeDate(ot)
                ?: run {
                    val all = dateValueRegex.findAll(line).map { it.value }.toList()
                    if (all.size >= 2) normalizeDate(all[1]) else null
                }

        return birth to death
    }

    private fun parsePlaceLine(line: String): String? {
        val fromLabel = placeFieldRegex.find(line)?.groupValues?.getOrNull(1)?.trim()
        val value = (fromLabel ?: line).trim().trimStart(':', '.', '-', ' ')
        if (value.isEmpty() || value == "-") return null
        if (value.count { it.isLetter() } < 2) return null
        return value
    }

    /**
     * Parse "Doğum Yeri:X" — everything after the label is the place (no required space after ":").
     */
    fun parseBirthPlaceLine(line: String): String? = parsePlaceLine(cleanupOcrNoise(line))

    /** Split a cleaned full-name line into given name(s) + surname. */
    fun splitNameLine(full: String?): Pair<String?, String?> {
        if (full.isNullOrBlank()) return null to null
        val parts =
            full
                .trim()
                .split(Regex("""\s+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "-" }
        return when {
            parts.isEmpty() -> null to null
            parts.size == 1 -> parts[0] to null
            else -> parts.dropLast(1).joinToString(" ") to parts.last()
        }
    }

    private fun splitName(full: String?): Pair<String?, String?> = splitNameLine(full)

    private fun normalizeDate(value: String?): String? {
        if (value.isNullOrBlank() || value == "-") return null
        val cleaned = value.replace(Regex("""[^\d.]"""), "").trim('.')
        val full = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""").matchEntire(cleaned)
        if (full != null) {
            val day = full.groupValues[1].padStart(2, '0')
            val month = full.groupValues[2].padStart(2, '0')
            val year = full.groupValues[3]
            return "$day.$month.$year"
        }
        if (Regex("""^\d{4}$""").matches(cleaned)) return cleaned
        return null
    }

    private fun cleanupOcrNoise(line: String): String =
        line
            .replace('|', 'I')
            .replace(Regex("""\s+"""), " ")
            .trim()
}
