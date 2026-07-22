package com.shejera.importing.cv

import com.shejera.importing.cv.steps.LinkedTreeNode
import com.shejera.importing.cv.steps.TreeLinkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeAssemblerTest {
    @Test
    fun chartChildrenBecomeGenealogicalParents() {
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 3,
                edgeCount = 2,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, parentId = null, childIds = listOf(1, 2), sex = "M", name = "ÖMER KARAHAN", role = "Kendi"),
                        LinkedTreeNode(id = 1, parentId = 0, childIds = emptyList(), sex = "F", name = "MERYEM KARAHAN", role = "Annesi"),
                        LinkedTreeNode(id = 2, parentId = 0, childIds = emptyList(), sex = "M", name = "ADEM KARAHAN", role = "Babası"),
                    ),
            )

        val recognized = TreeAssembler.fromStepTree(tree)
        assertEquals(3, recognized.people.size)
        assertTrue(recognized.people.any { it.tempId == "root" && it.givenName == "ÖMER" })

        val rootFamily = recognized.families.single { it.childTempIds == listOf("root") }
        assertEquals(listOf("c2", "c1"), rootFamily.spouseTempIds) // M then F
    }
}
