package com.shejera.importing.cv.steps

/**
 * Relationship text under the name (step 6).
 *
 * Layout: name (L1) → relationship (L2, may wrap to L3) → blank → dates…
 * We keep only the first relationship line; a wrap line is skipped.
 */
object RelationshipLineCleaner {
    private val dateOrPlaceHint =
        Regex("""(?iu)(?:^|\s)(?:dt|öt|ot|\)t|do[gğ]um\s*yer)""")

    fun clean(raw: String): String? {
        val lines =
            raw
                .lines()
                .map { cleanup(it) }
                .filter { it.isNotEmpty() }
                .filterNot { dateOrPlaceHint.containsMatchIn(it) }

        if (lines.isEmpty()) return null

        // Line 2 of the card = first line of this band; ignore wrap (3rd card line)
        val first = lines.first()
        if (first.count { it.isLetter() } < 3) return null
        return first
    }

    private fun cleanup(line: String): String =
        line
            .replace('|', 'I')
            .replace(Regex("""[©®™]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart(':', '.', '-', ' ')
}
