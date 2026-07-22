package com.shejera.importing.cv.steps

import com.shejera.importing.CvImageRecognizer
import com.shejera.importing.cv.BoxOcr
import com.shejera.importing.cv.ImagePreprocessor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Step 6: look at the relationship line under the name (card line 2).
 * If it wraps to a 3rd line, that wrap is skipped (not critical).
 *
 * Outputs: tree-with-roles.json, roles-overlay-on-source.png
 */
object RelationshipSkipStep {
    private val log = LoggerFactory.getLogger(RelationshipSkipStep::class.java)

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    data class Artifacts(
        val tree: TreeLinkResult,
        val treeJson: Path,
        val overlayOnSource: Path,
    )

    fun run(
        sourceImage: Path,
        boxes: List<DetectedBox>,
        tree: TreeLinkResult,
        outputDir: Path,
        tesseractLanguage: String = "tur+eng",
        tesseractDataPath: String? = null,
    ): Artifacts {
        require(Files.exists(sourceImage)) { "Source image not found: $sourceImage" }
        Files.createDirectories(outputDir)
        CvImageRecognizer.ensureOpenCvLoaded()

        val raw = ImagePreprocessor.loadBgr(sourceImage)
        val ocr = BoxOcr(tesseractLanguage, tesseractDataPath)
        ocr.warmUp()

        log.info("[cv-step6] relationship band (keep L2, skip wrap L3) for {} boxes", boxes.size)
        val roleById = LinkedHashMap<Int, String?>()
        for (box in boxes) {
            if (box.isRoot) {
                // Root is "Kendisi" — no parent relationship label needed
                roleById[box.id] = null
                continue
            }
            val rawBand =
                ocr.readRelationshipBand(
                    bgr = raw,
                    boxX = box.x,
                    boxY = box.y,
                    boxWidth = box.width,
                    boxHeight = box.height,
                    isRoot = false,
                )
            val role = RelationshipLineCleaner.clean(rawBand)
            roleById[box.id] = role
            log.info(
                "[cv-step6] box {} role='{}' raw='{}'",
                box.id,
                role,
                rawBand.replace("\n", " / "),
            )
        }

        val enrichedNodes =
            tree.nodes.map { node ->
                node.copy(role = roleById[node.id])
            }
        val enriched = tree.copy(nodes = enrichedNodes)

        val treeJson = outputDir.resolve("tree-with-roles.json")
        Files.writeString(treeJson, json.encodeToString(enriched))
        val withRole = enrichedNodes.count { !it.role.isNullOrBlank() }
        log.info("[cv-step6] wrote {} withRole={}/{}", treeJson, withRole, enrichedNodes.size)

        val overlayOnSource = outputDir.resolve("roles-overlay-on-source.png")
        writeOverlay(raw, boxes, enriched, overlayOnSource)
        log.info("[cv-step6] wrote {}", overlayOnSource)

        return Artifacts(
            tree = enriched,
            treeJson = treeJson,
            overlayOnSource = overlayOnSource,
        )
    }

    private fun writeOverlay(
        sourceBgr: Mat,
        boxes: List<DetectedBox>,
        tree: TreeLinkResult,
        outputPath: Path,
    ) {
        val canvas = sourceBgr.clone()
        val roleById = tree.nodes.associate { it.id to it.role }

        for (box in boxes) {
            val role = roleById[box.id]
            val color = Scalar(200.0, 100.0, 0.0)
            Imgproc.rectangle(
                canvas,
                Point(box.x.toDouble(), box.y.toDouble()),
                Point(box.xEnd.toDouble(), box.yEnd.toDouble()),
                color,
                1,
            )
            val label = "${box.id}: ${role ?: "(skip)"}"
            Imgproc.putText(
                canvas,
                label.take(48),
                Point(box.x + 6.0, box.y + 16.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.35,
                color,
                1,
            )
        }

        Files.createDirectories(outputPath.parent)
        Imgcodecs.imwrite(outputPath.toAbsolutePath().toString(), canvas)
    }
}
