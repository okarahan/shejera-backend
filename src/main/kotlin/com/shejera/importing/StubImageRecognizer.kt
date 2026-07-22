package com.shejera.importing

import com.shejera.models.RecognizedFamily
import com.shejera.models.RecognizedPerson
import com.shejera.models.RecognizedTree
import org.slf4j.LoggerFactory

/**
 * Placeholder recognizer for UI/flow testing without OpenCV/Tesseract.
 */
class StubImageRecognizer : ImageRecognizer {
    private val log = LoggerFactory.getLogger(StubImageRecognizer::class.java)

    override fun recognize(imagePath: java.nio.file.Path): RecognizedTree {
        log.warn("[import] using STUB recognizer for path={} (not real CV)", imagePath)
        return RecognizedTree(
            people =
                listOf(
                    RecognizedPerson(
                        tempId = "p1",
                        givenName = "Mehmet",
                        surname = "Karahan",
                        birthDate = "1834",
                        deathDate = "1910",
                        birthPlace = null,
                        sex = "M",
                        role = "Babası",
                    ),
                    RecognizedPerson(
                        tempId = "p2",
                        givenName = "Hesna",
                        surname = null,
                        birthDate = "01.07.1834",
                        deathDate = "11.09.1912",
                        birthPlace = null,
                        sex = "F",
                        role = "Annesi",
                    ),
                    RecognizedPerson(
                        tempId = "p0",
                        givenName = "Ali",
                        surname = "Karahan",
                        birthDate = "1860",
                        sex = "M",
                        role = "Kendi",
                    ),
                ),
            families =
                listOf(
                    RecognizedFamily(
                        tempId = "f1",
                        spouseTempIds = listOf("p1", "p2"),
                        childTempIds = listOf("p0"),
                    ),
                ),
        )
    }
}
