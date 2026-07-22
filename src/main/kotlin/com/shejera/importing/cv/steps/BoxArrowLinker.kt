package com.shejera.importing.cv.steps

import org.slf4j.LoggerFactory
import kotlin.math.hypot
import kotlin.math.max

/**
 * Step 3: match arrows ↔ boxes → parentId per child box.
 *
 * For each non-root box B:
 * 1. Pick the arrow whose tip is closest to B's top-center.
 * 2. Among boxes above B, pick the one closest to that arrow's start → parent.
 */
object BoxArrowLinker {
    private val log = LoggerFactory.getLogger(BoxArrowLinker::class.java)

    data class Link(
        val childId: Int,
        val parentId: Int?,
        val arrowId: Int?,
    )

    fun link(
        boxes: List<DetectedBox>,
        arrows: List<DetectedArrow>,
    ): List<Link> {
        val byId = boxes.associateBy { it.id }
        val links = mutableListOf<Link>()

        for (box in boxes) {
            if (box.isRoot) {
                links += Link(childId = box.id, parentId = null, arrowId = null)
                continue
            }

            val tipTargetX = box.centerX
            val tipTargetY = box.y
            val tipThreshold = max(40.0, box.width * 0.55)

            val arrow =
                arrows.minByOrNull { arrow ->
                    hypot(
                        (arrow.tipX - tipTargetX).toDouble(),
                        (arrow.tipY - tipTargetY).toDouble(),
                    )
                }?.takeIf { arrow ->
                    hypot(
                        (arrow.tipX - tipTargetX).toDouble(),
                        (arrow.tipY - tipTargetY).toDouble(),
                    ) <= tipThreshold
                }

            if (arrow == null) {
                log.warn("[cv-step3] box {} has no nearby arrow tip", box.id)
                links += Link(childId = box.id, parentId = null, arrowId = null)
                continue
            }

            val candidates =
                boxes.filter { parent ->
                    parent.id != box.id && parent.centerY < box.centerY - 8
                }
            if (candidates.isEmpty()) {
                links += Link(childId = box.id, parentId = null, arrowId = arrow.id)
                continue
            }

            val parent =
                candidates.minByOrNull { candidate ->
                    distanceToBox(arrow.startX, arrow.startY, candidate)
                }

            log.info(
                "[cv-step3] box {} ← parent {} via arrow {}",
                box.id,
                parent?.id,
                arrow.id,
            )
            links +=
                Link(
                    childId = box.id,
                    parentId = parent?.id,
                    arrowId = arrow.id,
                )
        }

        // Sanity: drop self-references / unknown ids
        return links.map { link ->
            val parent = link.parentId
            if (parent != null && (parent == link.childId || parent !in byId)) {
                link.copy(parentId = null)
            } else {
                link
            }
        }
    }

    fun toTreeResult(
        sourceFile: String,
        boxes: List<DetectedBox>,
        links: List<Link>,
    ): TreeLinkResult {
        val rootId = boxes.firstOrNull { it.isRoot }?.id ?: 0
        val parentOf = links.associate { it.childId to it.parentId }
        val arrowOf = links.associate { it.childId to it.arrowId }

        val childrenOf = mutableMapOf<Int, MutableList<Int>>()
        for (link in links) {
            val p = link.parentId ?: continue
            childrenOf.getOrPut(p) { mutableListOf() }.add(link.childId)
        }
        childrenOf.values.forEach { it.sort() }

        val nodes =
            boxes
                .sortedBy { it.id }
                .map { box ->
                    LinkedTreeNode(
                        id = box.id,
                        parentId = parentOf[box.id],
                        childIds = childrenOf[box.id]?.toList().orEmpty(),
                        arrowId = arrowOf[box.id],
                    )
                }

        val orphans =
            nodes
                .filter { it.id != rootId && it.parentId == null }
                .map { it.id }

        return TreeLinkResult(
            sourceFile = sourceFile,
            rootId = rootId,
            nodeCount = nodes.size,
            edgeCount = nodes.count { it.parentId != null },
            orphanIds = orphans,
            nodes = nodes,
        )
    }

    /** Distance from a point to the closest point on the box rectangle. */
    private fun distanceToBox(
        x: Int,
        y: Int,
        box: DetectedBox,
    ): Double {
        val cx = x.coerceIn(box.x, box.xEnd)
        val cy = y.coerceIn(box.y, box.yEnd)
        return hypot((x - cx).toDouble(), (y - cy).toDouble())
    }
}
