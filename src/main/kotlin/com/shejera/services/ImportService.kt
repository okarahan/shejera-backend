package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.NotFoundException
import com.shejera.importing.ImageRecognizer
import com.shejera.importing.ImportSession
import com.shejera.importing.ImportSessionStore
import com.shejera.models.ImportScanResponse
import com.shejera.models.ImportStatusResponse
import com.shejera.models.ImportUploadResponse
import com.shejera.models.RecognizedTree
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class ImportService(
    private val imageRecognizer: ImageRecognizer,
    private val recognizerMode: String,
    private val tempDir: Path = defaultTempDir(),
) {
    private val log = LoggerFactory.getLogger(ImportService::class.java)

    init {
        Files.createDirectories(tempDir)
    }

    fun upload(
        originalFileName: String,
        contentType: String?,
        bytes: ByteArray,
    ): ImportUploadResponse {
        if (bytes.isEmpty()) {
            throw BadRequestException("Uploaded file is empty")
        }
        if (!isAllowedImage(originalFileName, contentType)) {
            throw BadRequestException("Only image uploads are allowed (jpg, png, webp, gif)")
        }

        clearPreviousUpload()

        val extension = extensionOf(originalFileName) ?: "bin"
        val storedName = "${UUID.randomUUID()}.$extension"
        val target = tempDir.resolve(storedName).toAbsolutePath().normalize()
        Files.createDirectories(tempDir)
        Files.write(target, bytes)

        if (!Files.exists(target) || Files.size(target) != bytes.size.toLong()) {
            throw BadRequestException("Failed to persist uploaded image to filesystem")
        }

        val uploadedAt = Instant.now()
        ImportSessionStore.set(
            ImportSession(
                imagePath = target,
                originalFileName = originalFileName,
                contentType = contentType,
                sizeBytes = bytes.size.toLong(),
                uploadedAt = uploadedAt,
            ),
        )

        log.info(
            "[import] upload ok file={} bytes={} path={} recognizer={}",
            originalFileName,
            bytes.size,
            target,
            recognizerMode,
        )

        return ImportUploadResponse(
            originalFileName = originalFileName,
            storedFileName = storedName,
            storedPath = target.toString(),
            contentType = contentType,
            sizeBytes = bytes.size.toLong(),
            uploadedAt = uploadedAt.toString(),
        )
    }

    fun status(): ImportStatusResponse {
        val session = ImportSessionStore.current
        return ImportStatusResponse(
            hasUpload = session != null,
            originalFileName = session?.originalFileName,
            storedFileName = session?.imagePath?.fileName?.toString(),
            storedPath = session?.imagePath?.toAbsolutePath()?.toString(),
            uploadedAt = session?.uploadedAt?.toString(),
            hasScanResult = session?.recognizedTree != null,
            scannedAt = session?.scannedAt?.toString(),
            recognizer = recognizerMode,
        )
    }

    fun scan(): ImportScanResponse {
        val session =
            ImportSessionStore.current
                ?: throw BadRequestException("No uploaded image. Upload an image first.")

        if (!Files.exists(session.imagePath)) {
            throw BadRequestException("Uploaded image file is missing. Please upload again.")
        }

        val started = System.currentTimeMillis()
        log.info(
            "[import] scan start file={} path={} sizeBytes={} recognizer={}",
            session.originalFileName,
            session.imagePath,
            session.sizeBytes,
            recognizerMode,
        )

        val tree = imageRecognizer.recognize(session.imagePath)
        val scannedAt = Instant.now()

        ImportSessionStore.update { current ->
            current.copy(recognizedTree = tree, scannedAt = scannedAt)
        }

        log.info(
            "[import] scan done people={} families={} elapsedMs={} recognizer={}",
            tree.people.size,
            tree.families.size,
            System.currentTimeMillis() - started,
            recognizerMode,
        )

        return ImportScanResponse(
            scannedAt = scannedAt.toString(),
            personCount = tree.people.size,
            familyCount = tree.families.size,
            recognizer = recognizerMode,
        )
    }

    fun preview(): RecognizedTree {
        val session =
            ImportSessionStore.current
                ?: throw NotFoundException("No import session. Upload and scan an image first.")
        return session.recognizedTree
            ?: throw BadRequestException("No scan result yet. Run scan first.")
    }

    private fun clearPreviousUpload() {
        val previous = ImportSessionStore.current
        ImportSessionStore.clear()
        if (previous != null) {
            runCatching { Files.deleteIfExists(previous.imagePath) }
        }
    }

    companion object {
        fun defaultTempDir(): Path =
            Path.of(System.getProperty("java.io.tmpdir"), "shejera-imports")

        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

        private fun extensionOf(fileName: String): String? {
            val dot = fileName.lastIndexOf('.')
            if (dot < 0 || dot == fileName.lastIndex) return null
            return fileName.substring(dot + 1).lowercase()
        }

        private fun isAllowedImage(
            fileName: String,
            contentType: String?,
        ): Boolean {
            val ext = extensionOf(fileName)
            if (ext != null && ext in ALLOWED_EXTENSIONS) return true
            return contentType?.startsWith("image/") == true
        }
    }
}
