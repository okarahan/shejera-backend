package com.shejera.importing

import com.shejera.models.RecognizedTree
import java.nio.file.Path

fun interface ImageRecognizer {
    fun recognize(imagePath: Path): RecognizedTree
}
