package com.shejera.importing

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.ApplicationConfig

data class ImageRecognizerSetup(
    val recognizer: ImageRecognizer,
    val mode: String,
)

fun Application.createImageRecognizerSetup(): ImageRecognizerSetup {
    val config = environment.config.config("shejera.import")
    val setup = createImageRecognizerSetup(config)
    log.info("Import image recognizer: {}", setup.mode)
    return setup
}

fun createImageRecognizerSetup(config: ApplicationConfig): ImageRecognizerSetup {
    val mode = config.propertyOrNull("recognizer")?.getString()?.lowercase() ?: "cv"
    val recognizer =
        when (mode) {
            "stub" -> StubImageRecognizer()
            "cv" ->
                CvImageRecognizer(
                    tesseractLanguage =
                        config.propertyOrNull("tesseractLanguage")?.getString() ?: "tur+eng",
                    tesseractDataPath =
                        config.propertyOrNull("tesseractDataPath")?.getString()?.ifBlank { null },
                )
            else -> StubImageRecognizer()
        }
    val resolvedMode =
        when (recognizer) {
            is CvImageRecognizer -> "cv"
            is StubImageRecognizer -> "stub"
            else -> mode
        }
    return ImageRecognizerSetup(recognizer = recognizer, mode = resolvedMode)
}
