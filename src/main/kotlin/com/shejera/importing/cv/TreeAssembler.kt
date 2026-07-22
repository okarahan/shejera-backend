package com.shejera.importing.cv

import com.shejera.importing.cv.steps.LinkedTreeNode
import com.shejera.importing.cv.steps.TreeLinkResult
import com.shejera.models.RecognizedFamily
import com.shejera.models.RecognizedPerson
import com.shejera.models.RecognizedTree

/**
 * Maps the step-pipeline chart tree into the import preview model.
 *
 * Chart edges point downward (root → ancestors). Genealogically that is inverted:
 * nodes with [LinkedTreeNode.parentId] == person are that person's parents.
 */
object TreeAssembler {
    fun fromStepTree(tree: TreeLinkResult): RecognizedTree {
        val people =
            tree.nodes.map { node ->
                val (given, surname) = CardTextParser.splitNameLine(node.name)
                RecognizedPerson(
                    tempId = tempId(node, tree.rootId),
                    givenName = given,
                    surname = surname,
                    birthDate = node.birthDate,
                    deathDate = node.deathDate,
                    birthPlace = node.birthPlace,
                    sex = node.sex,
                    role = node.role,
                )
            }

        val byId = tree.nodes.associateBy { it.id }
        val families = mutableListOf<RecognizedFamily>()
        var familyIndex = 1

        for (person in tree.nodes) {
            val geneaParents =
                person.childIds.mapNotNull { byId[it] }.ifEmpty {
                    tree.nodes.filter { it.parentId == person.id }
                }
            if (geneaParents.isEmpty()) continue

            families +=
                RecognizedFamily(
                    tempId = "f${familyIndex++}",
                    spouseTempIds = orderSpouses(geneaParents).map { tempId(it, tree.rootId) },
                    childTempIds = listOf(tempId(person, tree.rootId)),
                )
        }

        return RecognizedTree(people = people, families = families)
    }

    fun tempId(
        node: LinkedTreeNode,
        rootId: Int,
    ): String = if (node.id == rootId) "root" else "c${node.id}"

    private fun orderSpouses(parents: List<LinkedTreeNode>): List<LinkedTreeNode> {
        if (parents.size <= 1) return parents
        val father = parents.firstOrNull { it.sex == "M" }
        val mother = parents.firstOrNull { it.sex == "F" }
        return when {
            father != null && mother != null && father.id != mother.id ->
                listOf(father, mother)
            else -> parents.sortedBy { it.id }
        }
    }
}
