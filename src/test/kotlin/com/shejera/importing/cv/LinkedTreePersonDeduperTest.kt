package com.shejera.importing.cv

import com.shejera.importing.cv.steps.LinkedTreeNode
import com.shejera.importing.cv.steps.TreeLinkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkedTreePersonDeduperTest {
    @Test
    fun mergesChartDuplicatesSharingParentProfiles() {
        // e-Devlet style: same couple appears under two descendants (ids 9 and 12).
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 7,
                edgeCount = 6,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, parentId = null, childIds = listOf(9, 12), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 9,
                            parentId = 0,
                            childIds = listOf(16, 17),
                            name = "HACI MEHMET",
                            sex = "M",
                            birthDate = "02.08.1904",
                            deathDate = "11.03.1933",
                        ),
                        LinkedTreeNode(
                            id = 12,
                            parentId = 0,
                            childIds = listOf(22, 23),
                            name = "HALİL İBRAHİM KARAHAN",
                            sex = "M",
                            birthDate = "01.07.1892",
                            deathDate = "29.12.1942",
                        ),
                        LinkedTreeNode(
                            id = 16,
                            parentId = 9,
                            childIds = listOf(28),
                            name = "SEYİDİ AHMET",
                            sex = "M",
                            birthDate = "01.07.1864",
                            deathDate = "27.12.1899",
                        ),
                        LinkedTreeNode(
                            id = 22,
                            parentId = 12,
                            childIds = listOf(29),
                            name = "SEYİDİ AHMET",
                            sex = "M",
                            birthDate = "01.07.1864",
                            deathDate = "27.12.1899",
                        ),
                        LinkedTreeNode(
                            id = 17,
                            parentId = 9,
                            childIds = emptyList(),
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.07.1854",
                            deathDate = "1917",
                        ),
                        LinkedTreeNode(
                            id = 23,
                            parentId = 12,
                            childIds = emptyList(),
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.07.1854",
                            deathDate = "1917",
                        ),
                        LinkedTreeNode(
                            id = 28,
                            parentId = 16,
                            childIds = emptyList(),
                            name = "HESNA",
                            sex = "F",
                            birthDate = "01.07.1834",
                            deathDate = "11.09.1912",
                        ),
                        LinkedTreeNode(
                            id = 29,
                            parentId = 22,
                            childIds = emptyList(),
                            name = "HESNA",
                            sex = "F",
                            birthDate = "01.07.1834",
                            deathDate = "11.09.1912",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        val names = deduped.nodes.map { it.name }
        assertEquals(1, names.count { it == "SEYİDİ AHMET" })
        assertEquals(1, names.count { it == "AYŞE KARAHAN" })
        assertEquals(1, names.count { it == "HESNA" })
        assertEquals(6, deduped.nodes.size)

        val seyidi = deduped.nodes.single { it.name == "SEYİDİ AHMET" }
        // Both descendants (9 and 12) remain parents in the chart sense
        assertTrue(seyidi.parentId == 9 || seyidi.parentId == 12)
        val otherParent = if (seyidi.parentId == 9) 12 else 9
        // Chart child links from both branches should point at the canonical couple
        val p9 = deduped.nodes.single { it.id == 9 }
        val p12 = deduped.nodes.single { it.id == 12 }
        assertTrue(seyidi.id in p9.childIds || seyidi.id in p12.childIds)
        assertEquals(
            setOf(seyidi.id, deduped.nodes.single { it.name == "AYŞE KARAHAN" }.id),
            (p9.childIds + p12.childIds).toSet(),
        )
        assertTrue(otherParent in setOf(9, 12))

        val hesna = deduped.nodes.single { it.name == "HESNA" }
        assertEquals(seyidi.id, hesna.parentId)
        assertEquals(listOf(hesna.id), seyidi.childIds)
    }

    @Test
    fun doesNotMergeWhenBirthDatesDiffer() {
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 3,
                edgeCount = 2,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, childIds = listOf(1, 2), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 1,
                            parentId = 0,
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.01.1900",
                        ),
                        LinkedTreeNode(
                            id = 2,
                            parentId = 0,
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.01.1910",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        assertEquals(3, deduped.nodes.size)
    }

    @Test
    fun doesNotMergeWhenBirthPlacesDiffer() {
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 3,
                edgeCount = 2,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, childIds = listOf(1, 2), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 1,
                            parentId = 0,
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.01.1900",
                            birthPlace = "ANKARA",
                        ),
                        LinkedTreeNode(
                            id = 2,
                            parentId = 0,
                            name = "AYŞE KARAHAN",
                            sex = "F",
                            birthDate = "01.01.1900",
                            birthPlace = "İSTANBUL",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        assertEquals(3, deduped.nodes.size)
    }

    @Test
    fun mergesParentlessDuplicatesViaSharedChildProfiles() {
        // Top-of-tree: mother drawn twice under two copies of the same child, no ancestors.
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 5,
                edgeCount = 4,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, childIds = listOf(1, 2), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 1,
                            parentId = 0,
                            childIds = listOf(10),
                            name = "SEYİDİ AHMET",
                            sex = "M",
                            birthDate = "01.07.1864",
                            deathDate = "27.12.1899",
                        ),
                        LinkedTreeNode(
                            id = 2,
                            parentId = 0,
                            childIds = listOf(11),
                            name = "SEYİDİ AHMET",
                            sex = "M",
                            birthDate = "01.07.1864",
                            deathDate = "27.12.1899",
                        ),
                        LinkedTreeNode(
                            id = 10,
                            parentId = 1,
                            name = "HESNA",
                            sex = "F",
                            birthDate = "01.07.1834",
                            deathDate = "11.09.1912",
                        ),
                        LinkedTreeNode(
                            id = 11,
                            parentId = 2,
                            name = "HESNA",
                            sex = "F",
                            birthDate = "01.07.1834",
                            deathDate = "11.09.1912",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        assertEquals(1, deduped.nodes.count { it.name == "SEYİDİ AHMET" })
        assertEquals(1, deduped.nodes.count { it.name == "HESNA" })
        assertEquals(3, deduped.nodes.size)
        val seyidi = deduped.nodes.single { it.name == "SEYİDİ AHMET" }
        val hesna = deduped.nodes.single { it.name == "HESNA" }
        assertEquals(seyidi.id, hesna.parentId)
        assertEquals(listOf(hesna.id), seyidi.childIds)
    }

    @Test
    fun doesNotMergeParentlessWhenChildProfilesDiffer() {
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 5,
                edgeCount = 4,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, childIds = listOf(1, 2), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 1,
                            parentId = 0,
                            childIds = listOf(10),
                            name = "CHILD A",
                            sex = "M",
                            birthDate = "01.01.1900",
                        ),
                        LinkedTreeNode(
                            id = 2,
                            parentId = 0,
                            childIds = listOf(11),
                            name = "CHILD B",
                            sex = "M",
                            birthDate = "01.01.1901",
                        ),
                        LinkedTreeNode(
                            id = 10,
                            parentId = 1,
                            name = "MOTHER SAME",
                            sex = "F",
                            birthDate = "01.01.1870",
                        ),
                        LinkedTreeNode(
                            id = 11,
                            parentId = 2,
                            name = "MOTHER SAME",
                            sex = "F",
                            birthDate = "01.01.1870",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        assertEquals(2, deduped.nodes.count { it.name == "MOTHER SAME" })
    }

    @Test
    fun doesNotMergeWhenFatherProfilesDiffer() {
        val tree =
            TreeLinkResult(
                sourceFile = "test.png",
                rootId = 0,
                nodeCount = 5,
                edgeCount = 4,
                nodes =
                    listOf(
                        LinkedTreeNode(id = 0, childIds = listOf(1, 2), name = "ROOT", sex = "M"),
                        LinkedTreeNode(
                            id = 1,
                            parentId = 0,
                            childIds = listOf(10),
                            name = "CHILD SAME",
                            sex = "M",
                            birthDate = "01.01.1920",
                        ),
                        LinkedTreeNode(
                            id = 2,
                            parentId = 0,
                            childIds = listOf(11),
                            name = "CHILD SAME",
                            sex = "M",
                            birthDate = "01.01.1920",
                        ),
                        LinkedTreeNode(
                            id = 10,
                            parentId = 1,
                            name = "FATHER A",
                            sex = "M",
                            birthDate = "01.01.1890",
                        ),
                        LinkedTreeNode(
                            id = 11,
                            parentId = 2,
                            name = "FATHER B",
                            sex = "M",
                            birthDate = "01.01.1890",
                        ),
                    ),
            )

        val deduped = LinkedTreePersonDeduper.dedupe(tree)
        assertEquals(2, deduped.nodes.count { it.name == "CHILD SAME" })
    }
}
