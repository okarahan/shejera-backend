package com.shejera.importing

import com.shejera.models.RecognizedTree
import java.nio.file.Path
import java.time.Instant

data class ImportSession(
    val imagePath: Path,
    val originalFileName: String,
    val contentType: String?,
    val sizeBytes: Long,
    val uploadedAt: Instant,
    val recognizedTree: RecognizedTree? = null,
    val scannedAt: Instant? = null,
)
