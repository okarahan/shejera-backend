package com.shejera.importing.cv.steps

import kotlinx.serialization.Serializable

/**
 * Chart tree from step 3 (+ optional sex from step 4).
 */
@Serializable
data class LinkedTreeNode(
    val id: Int,
    val parentId: Int? = null,
    val childIds: List<Int> = emptyList(),
    /** Arrow used for this link (null for root / orphans). */
    val arrowId: Int? = null,
    /** "M" | "F" from left gender bar (step 4); null if unknown. */
    val sex: String? = null,
    /** Full name from first line OCR (step 5); dashes removed. */
    val name: String? = null,
    /**
     * Relationship label under the name (step 6), first line only.
     * Wrap line (3rd text line) is skipped — not critical for the tree.
     */
    val role: String? = null,
    /** Birth date from DT field (step 7), normalized `DD.MM.YYYY` when possible. */
    val birthDate: String? = null,
    /** Death date from ÖT field (step 7); null if "-" / missing. */
    val deathDate: String? = null,
    /** Birth place from "Doğum Yeri:…" (step 8). */
    val birthPlace: String? = null,
)

@Serializable
data class TreeLinkResult(
    val sourceFile: String,
    val rootId: Int,
    val nodeCount: Int,
    val edgeCount: Int,
    val orphanIds: List<Int> = emptyList(),
    val nodes: List<LinkedTreeNode>,
)
