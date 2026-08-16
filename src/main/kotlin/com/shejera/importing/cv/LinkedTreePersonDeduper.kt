package com.shejera.importing.cv

import com.shejera.importing.cv.steps.LinkedTreeNode
import com.shejera.importing.cv.steps.TreeLinkResult
import org.slf4j.LoggerFactory

/**
 * Post-process after OCR: collapse e-Devlet chart duplicates where the same
 * ancestor appears once on each branch.
 *
 * Identity:
 * - self: given, surname, sex, birthDate, deathDate, birthPlace
 * - if genealogical parents exist: father + mother profiles (same fields)
 * - if no parents: co-parent (spouse) profiles of genea children when present,
 *   otherwise genea children profiles (same fields);
 *   nodes with neither match on self only
 *
 * Nodes without a usable name are never merged.
 */
object LinkedTreePersonDeduper {
    private val log = LoggerFactory.getLogger(LinkedTreePersonDeduper::class.java)

    fun dedupe(tree: TreeLinkResult): TreeLinkResult {
        if (tree.nodes.size < 2) return tree

        val byId = tree.nodes.associateBy { it.id }
        val keyToIds = linkedMapOf<String, MutableList<Int>>()

        for (node in tree.nodes) {
            val key = identityKey(node, byId) ?: continue
            keyToIds.getOrPut(key) { mutableListOf() }.add(node.id)
        }

        val mergeGroups = keyToIds.values.filter { it.size > 1 }
        if (mergeGroups.isEmpty()) {
            log.info("[cv-dedupe] no duplicate identity keys")
            return tree
        }

        val redirect = mutableMapOf<Int, Int>()
        for (group in mergeGroups) {
            val members = group.mapNotNull { byId[it] }
            val canonical = pickCanonical(members, tree.rootId)
            for (member in members) {
                if (member.id != canonical.id) {
                    redirect[member.id] = canonical.id
                }
            }
            log.info(
                "[cv-dedupe] merge {} → {} ({})",
                group.filter { it != canonical.id },
                canonical.id,
                canonical.name,
            )
        }

        if (redirect.isEmpty()) return tree

        fun canon(id: Int): Int {
            var cur = id
            val seen = mutableSetOf<Int>()
            while (cur in redirect && cur !in seen) {
                seen += cur
                cur = redirect.getValue(cur)
            }
            return cur
        }

        val survivors =
            tree.nodes
                .filter { it.id !in redirect }
                .map { node ->
                    val mergedFrom =
                        tree.nodes.filter { canon(it.id) == node.id }
                    mergeFields(node, mergedFrom).copy(
                        parentId = node.parentId?.let { canon(it) }?.takeIf { it != node.id },
                        childIds =
                            mergedFrom
                                .flatMap { it.childIds }
                                .map { canon(it) }
                                .filter { it != node.id }
                                .distinct()
                                .sorted(),
                    )
                }

        val survivorIds = survivors.map { it.id }.toSet()
        val edgeCount =
            survivors.sumOf { n ->
                (if (n.parentId != null) 1 else 0) + n.childIds.size
            } / 2

        return tree.copy(
            nodeCount = survivors.size,
            edgeCount = edgeCount,
            orphanIds =
                tree.orphanIds
                    .map { canon(it) }
                    .filter { it in survivorIds }
                    .distinct(),
            nodes = survivors.sortedBy { it.id },
            rootId = canon(tree.rootId),
        )
    }

    internal fun identityKey(
        node: LinkedTreeNode,
        byId: Map<Int, LinkedTreeNode>,
    ): String? {
        val self = personFingerprint(node) ?: return null
        val (father, mother) = geneaParents(node, byId)
        val hasParents = father != null || mother != null
        val relative =
            if (hasParents) {
                listOf(
                    "PARENTS",
                    parentFingerprint(father),
                    parentFingerprint(mother),
                ).joinToString("|")
            } else {
                noParentsRelativeKey(node, byId)
            }
        return "$self|$relative"
    }

    /**
     * When ancestors are missing on the chart, match via spouses first (same couple
     * under two sibling branches), else via genea children (top-of-tree parents).
     */
    private fun noParentsRelativeKey(
        node: LinkedTreeNode,
        byId: Map<Int, LinkedTreeNode>,
    ): String {
        val children = geneaChildren(node, byId)
        val spouses =
            children
                .flatMap { child ->
                    val (father, mother) = geneaParents(child, byId)
                    listOfNotNull(father, mother).filter { it.id != node.id }
                }.distinctBy { it.id }

        fun fps(nodes: List<LinkedTreeNode>): String =
            nodes
                .map { personFingerprint(it) ?: "UNNAMED:${it.id}" }
                .sorted()
                .joinToString(",")

        return when {
            spouses.isNotEmpty() -> "SPOUSES|${fps(spouses)}"
            children.isNotEmpty() -> "CHILDREN|${fps(children)}"
            else -> "NO_RELATIVES"
        }
    }

    /**
     * Genealogical parents are chart children (ancestors above the card).
     */
    private fun geneaParents(
        node: LinkedTreeNode,
        byId: Map<Int, LinkedTreeNode>,
    ): Pair<LinkedTreeNode?, LinkedTreeNode?> {
        val parents =
            node.childIds.mapNotNull { byId[it] }.ifEmpty {
                byId.values.filter { it.parentId == node.id }
            }
        val fathers = parents.filter { it.sex.equals("M", ignoreCase = true) }
        val mothers = parents.filter { it.sex.equals("F", ignoreCase = true) }
        val fatherOut =
            when {
                fathers.size > 1 -> ambiguousParent(fathers)
                else -> fathers.singleOrNull()
            }
        val motherOut =
            when {
                mothers.size > 1 -> ambiguousParent(mothers)
                else -> mothers.singleOrNull()
            }
        return fatherOut to motherOut
    }

    /**
     * Genealogical children: cards that list [node] among their chart children
     * (ancestors), or whose chart parentId points at [node].
     */
    private fun geneaChildren(
        node: LinkedTreeNode,
        byId: Map<Int, LinkedTreeNode>,
    ): List<LinkedTreeNode> {
        val viaChildIds =
            byId.values.filter { other ->
                other.id != node.id && node.id in other.childIds
            }
        if (viaChildIds.isNotEmpty()) return viaChildIds
        return byId.values.filter { it.parentId == node.id }
    }

    private fun ambiguousParent(nodes: List<LinkedTreeNode>): LinkedTreeNode =
        LinkedTreeNode(
            id = -1,
            name = "AMBIGUOUS:" + nodes.map { normalizeName(it.name) }.sorted().joinToString(","),
            sex = nodes.firstOrNull()?.sex,
        )

    /** Null if the node has no usable name (cannot participate in dedupe). */
    private fun personFingerprint(node: LinkedTreeNode): String? {
        val (given, surname) = CardTextParser.splitNameLine(node.name)
        if (given.isNullOrBlank() && surname.isNullOrBlank()) return null
        return listOf(
            normalizeName(given),
            normalizeName(surname),
            normalizeSex(node.sex),
            normalizeDate(node.birthDate),
            normalizeDate(node.deathDate),
            normalizeName(node.birthPlace),
        ).joinToString(";")
    }

    /** Absent parent → empty fingerprint (matches other absent). */
    private fun parentFingerprint(parent: LinkedTreeNode?): String {
        if (parent == null) return "ABSENT"
        return personFingerprint(parent) ?: "ABSENT"
    }

    private fun pickCanonical(
        members: List<LinkedTreeNode>,
        rootId: Int,
    ): LinkedTreeNode {
        members.firstOrNull { it.id == rootId }?.let { return it }
        return members
            .sortedWith(
                compareByDescending<LinkedTreeNode> { filledFieldCount(it) }
                    .thenBy { it.id },
            ).first()
    }

    private fun filledFieldCount(node: LinkedTreeNode): Int =
        listOf(node.name, node.sex, node.birthDate, node.deathDate, node.birthPlace, node.role)
            .count { !it.isNullOrBlank() }

    private fun mergeFields(
        canonical: LinkedTreeNode,
        members: List<LinkedTreeNode>,
    ): LinkedTreeNode {
        fun pick(selector: (LinkedTreeNode) -> String?): String? =
            selector(canonical)?.takeIf { it.isNotBlank() }
                ?: members.mapNotNull { selector(it)?.takeIf { v -> v.isNotBlank() } }.firstOrNull()

        return canonical.copy(
            name = pick { it.name },
            sex = pick { it.sex },
            role = pick { it.role },
            birthDate = pick { it.birthDate },
            deathDate = pick { it.deathDate },
            birthPlace = pick { it.birthPlace },
            arrowId = canonical.arrowId ?: members.mapNotNull { it.arrowId }.firstOrNull(),
        )
    }

    private fun normalizeName(value: String?): String =
        value
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.uppercase()
            .orEmpty()

    private fun normalizeSex(value: String?): String =
        value?.trim()?.uppercase().orEmpty()

    private fun normalizeDate(value: String?): String =
        value?.trim().orEmpty()
}
