package com.shejera.importing

import com.shejera.api.BadRequestException
import com.shejera.importing.cv.TreeAssembler
import com.shejera.importing.cv.steps.ArrowDetectionStep
import com.shejera.importing.cv.steps.BirthPlaceOcrStep
import com.shejera.importing.cv.steps.BoxDetectionStep
import com.shejera.importing.cv.steps.DatesOcrStep
import com.shejera.importing.cv.steps.NameOcrStep
import com.shejera.importing.cv.steps.RelationshipSkipStep
import com.shejera.importing.cv.steps.SexEnrichmentStep
import com.shejera.importing.cv.steps.TreeLinkStep
import com.shejera.models.RecognizedTree
import nu.pattern.OpenCV
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Production image recognizer: runs CV steps 1–8 and maps the result to [RecognizedTree].
 */
class CvImageRecognizer(
    private val tesseractLanguage: String = "tur+eng",
    private val tesseractDataPath: String? = null,
) : ImageRecognizer {
    private val log = LoggerFactory.getLogger(CvImageRecognizer::class.java)

    override fun recognize(imagePath: Path): RecognizedTree {
        val totalStarted = System.currentTimeMillis()
        log.info("[cv] step=start path={}", imagePath)

        val workDir = Files.createTempDirectory("shejera-cv-")
        return try {
            ensureOpenCvLoaded()

            log.info("[cv] step=1-boxes")
            val boxes =
                BoxDetectionStep
                    .run(imagePath, workDir.resolve("step1"))
                    .result
            if (boxes.boxes.isEmpty()) {
                throw BadRequestException(
                    "No person cards detected in image. Check image quality and layout.",
                )
            }
            log.info("[cv] step=1-boxes done count={}", boxes.boxes.size)

            log.info("[cv] step=2-arrows")
            val arrows =
                ArrowDetectionStep
                    .run(imagePath, boxes.boxes, workDir.resolve("step2"))
                    .result
            log.info("[cv] step=2-arrows done count={}", arrows.arrows.size)

            log.info("[cv] step=3-tree-link")
            var tree =
                TreeLinkStep
                    .run(imagePath, boxes.boxes, arrows.arrows, workDir.resolve("step3"))
                    .tree
            log.info(
                "[cv] step=3-tree-link done nodes={} edges={} orphans={}",
                tree.nodeCount,
                tree.edgeCount,
                tree.orphanIds,
            )

            log.info("[cv] step=4-sex")
            tree =
                SexEnrichmentStep
                    .run(imagePath, boxes.boxes, tree, workDir.resolve("step4"))
                    .tree
            log.info("[cv] step=4-sex done")

            log.info("[cv] step=5-names")
            tree =
                NameOcrStep
                    .run(
                        imagePath,
                        boxes.boxes,
                        tree,
                        workDir.resolve("step5"),
                        tesseractLanguage,
                        tesseractDataPath,
                    ).tree
            log.info("[cv] step=5-names done")

            log.info("[cv] step=6-roles")
            tree =
                RelationshipSkipStep
                    .run(
                        imagePath,
                        boxes.boxes,
                        tree,
                        workDir.resolve("step6"),
                        tesseractLanguage,
                        tesseractDataPath,
                    ).tree
            log.info("[cv] step=6-roles done")

            log.info("[cv] step=7-dates")
            tree =
                DatesOcrStep
                    .run(
                        imagePath,
                        boxes.boxes,
                        tree,
                        workDir.resolve("step7"),
                        tesseractLanguage,
                        tesseractDataPath,
                    ).tree
            log.info("[cv] step=7-dates done")

            log.info("[cv] step=8-places")
            tree =
                BirthPlaceOcrStep
                    .run(
                        imagePath,
                        boxes.boxes,
                        tree,
                        workDir.resolve("step8"),
                        tesseractLanguage,
                        tesseractDataPath,
                    ).tree
            log.info("[cv] step=8-places done")

            val recognized = TreeAssembler.fromStepTree(tree)
            log.info(
                "[cv] step=done people={} families={} elapsedMs={}",
                recognized.people.size,
                recognized.families.size,
                System.currentTimeMillis() - totalStarted,
            )
            recognized
        } catch (ex: BadRequestException) {
            log.warn("[cv] step=failed (bad request): {}", ex.message)
            throw ex
        } catch (ex: Exception) {
            log.error("[cv] step=failed elapsedMs={}", System.currentTimeMillis() - totalStarted, ex)
            throw BadRequestException(
                "Image recognition failed: ${ex.message ?: ex.javaClass.simpleName}",
            )
        } finally {
            @OptIn(ExperimentalPathApi::class)
            runCatching { workDir.deleteRecursively() }
                .onFailure { log.debug("[cv] temp cleanup failed: {}", it.message) }
        }
    }

    companion object {
        private val openCvLoaded = AtomicBoolean(false)
        private val loadLog = LoggerFactory.getLogger(CvImageRecognizer::class.java)

        fun ensureOpenCvLoaded() {
            if (openCvLoaded.get()) return
            synchronized(this) {
                if (openCvLoaded.get()) return
                loadLog.info("[cv] loading OpenCV native library…")
                OpenCV.loadLocally()
                openCvLoaded.set(true)
                loadLog.info("[cv] OpenCV native library loaded")
            }
        }
    }
}
