package com.shejera.models

import kotlinx.serialization.Serializable

@Serializable
data class ImportUploadResponse(
    val originalFileName: String,
    val storedFileName: String,
    val storedPath: String,
    val contentType: String? = null,
    val sizeBytes: Long,
    val uploadedAt: String,
)

@Serializable
data class ImportStatusResponse(
    val hasUpload: Boolean,
    val originalFileName: String? = null,
    val storedFileName: String? = null,
    val storedPath: String? = null,
    val uploadedAt: String? = null,
    val hasScanResult: Boolean,
    val scannedAt: String? = null,
    val recognizer: String,
)

@Serializable
data class ImportScanResponse(
    val scannedAt: String,
    val personCount: Int,
    val familyCount: Int,
    val recognizer: String,
)

@Serializable
data class RecognizedPerson(
    val tempId: String,
    val givenName: String? = null,
    val surname: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val birthPlace: String? = null,
    val sex: String? = null,
    val role: String? = null,
)

@Serializable
data class RecognizedFamily(
    val tempId: String,
    val spouseTempIds: List<String> = emptyList(),
    val childTempIds: List<String> = emptyList(),
)

@Serializable
data class RecognizedTree(
    val people: List<RecognizedPerson> = emptyList(),
    val families: List<RecognizedFamily> = emptyList(),
)
