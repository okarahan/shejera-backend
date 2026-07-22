package com.shejera.importing.cv.steps

import kotlin.test.Test
import kotlin.test.assertEquals

class BoxReadingOrderTest {
    @Test
    fun rootIsZeroThenRowsLeftToRight() {
        // Layout (centers):
        //        R
        //    A         B
        //  C   D   E   F
        val boxes =
            listOf(
                box(isRoot = false, x = 4000, y = 480, w = 340, h = 120), // B
                box(isRoot = true, x = 3000, y = 180, w = 250, h = 250), // R
                box(isRoot = false, x = 4700, y = 640, w = 340, h = 120), // F
                box(isRoot = false, x = 800, y = 640, w = 340, h = 120), // C
                box(isRoot = false, x = 1500, y = 480, w = 340, h = 120), // A
                box(isRoot = false, x = 3300, y = 640, w = 340, h = 120), // E
                box(isRoot = false, x = 1900, y = 640, w = 340, h = 120), // D
            )

        val ordered = BoxDetectionStep.assignReadingOrderIds(boxes)

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), ordered.map { it.id })
        assertEquals(true, ordered[0].isRoot)
        // A, B
        assertEquals(1500, ordered[1].x)
        assertEquals(4000, ordered[2].x)
        // C, D, E, F
        assertEquals(800, ordered[3].x)
        assertEquals(1900, ordered[4].x)
        assertEquals(3300, ordered[5].x)
        assertEquals(4700, ordered[6].x)
    }

    private fun box(
        isRoot: Boolean,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = DetectedBox(
        id = -1,
        x = x,
        y = y,
        width = w,
        height = h,
        xEnd = x + w - 1,
        yEnd = y + h - 1,
        centerX = x + w / 2,
        centerY = y + h / 2,
        isRoot = isRoot,
    )
}
