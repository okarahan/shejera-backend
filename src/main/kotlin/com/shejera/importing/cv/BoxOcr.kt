package com.shejera.importing.cv

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.leptonica.PIX
import org.bytedeco.leptonica.global.leptonica
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import org.slf4j.LoggerFactory

/**
 * OCR via bundled Bytedeco Tesseract natives (no system Tesseract install).
 */
class BoxOcr(
    private val language: String,
    private val dataPath: String?,
) {
    private val log = LoggerFactory.getLogger(BoxOcr::class.java)

    private val api: TessBaseAPI by lazy {
        log.info("[cv] tesseract Init language={} …", language)
        val started = System.currentTimeMillis()
        val tess = TessBaseAPI()
        val path = TessdataBundle.resolveDataPath(dataPath)
        log.info("[cv] tesseract tessdata path={}", path)
        val init = tess.Init(path, language)
        if (init != 0) {
            tess.End()
            throw IllegalStateException("Failed to initialize bundled Tesseract at $path")
        }
        tess.SetPageSegMode(tesseract.PSM_SINGLE_BLOCK)
        tess.SetVariable("user_defined_dpi", "300")
        log.info("[cv] tesseract Init ok elapsedMs={}", System.currentTimeMillis() - started)
        tess
    }

    /** Touch TessBaseAPI so Init runs before the first card OCR. */
    fun warmUp() {
        api
    }

    /**
     * OCR only the top name band of a step-1 box (first line).
     * Skips the left gender bar. Uses single-line page segmentation.
     */
    fun readFirstLineName(
        bgr: Mat,
        boxX: Int,
        boxY: Int,
        boxWidth: Int,
        boxHeight: Int,
        isRoot: Boolean,
    ): String {
        val barCrop =
            if (isRoot) {
                0
            } else {
                // Gender bar is only ~8–14px; larger crops clip the first letter of the name
                maxOf(6, minOf(12, (boxWidth * 0.04).toInt()))
            }
        val padX = 1
        val padY = 1
        val x = (boxX + barCrop + padX).coerceIn(0, bgr.cols() - 1)
        val y = (boxY + padY).coerceIn(0, bgr.rows() - 1)
        val w = (boxWidth - barCrop - 2 * padX).coerceAtLeast(1).coerceAtMost(bgr.cols() - x)
        // Name is the first line — top band; keep modest so relationship line stays out
        val bandRatio = if (isRoot) 0.36 else 0.26
        val h =
            (boxHeight * bandRatio).toInt()
                .coerceAtLeast(16)
                .coerceAtMost(bgr.rows() - y)
                .coerceAtMost(boxHeight - padY)

        return ocrRoi(Mat(bgr, Rect(x, y, w, h.coerceAtLeast(1))), singleLine = true)
    }

    /**
     * OCR the relationship band under the name (up to two text lines).
     * Caller should keep line 1 and skip a wrap on line 2 of this band.
     */
    fun readRelationshipBand(
        bgr: Mat,
        boxX: Int,
        boxY: Int,
        boxWidth: Int,
        boxHeight: Int,
        isRoot: Boolean,
    ): String {
        val barCrop =
            if (isRoot) {
                0
            } else {
                maxOf(6, minOf(12, (boxWidth * 0.04).toInt()))
            }
        val padX = 1
        val nameBandRatio = if (isRoot) 0.36 else 0.26
        val nameBandH = (boxHeight * nameBandRatio).toInt().coerceAtLeast(16)
        // Start just below the name band
        val x = (boxX + barCrop + padX).coerceIn(0, bgr.cols() - 1)
        val y = (boxY + nameBandH).coerceIn(0, bgr.rows() - 1)
        val w = (boxWidth - barCrop - 2 * padX).coerceAtLeast(1).coerceAtMost(bgr.cols() - x)
        // Enough for ~2 relationship lines; stay above DT/ÖT zone (~bottom 35%)
        val maxBottom = boxY + (boxHeight * 0.62).toInt()
        val h =
            (maxBottom - y)
                .coerceAtLeast(12)
                .coerceAtMost(bgr.rows() - y)
                .coerceAtMost((boxHeight * 0.38).toInt())

        if (h < 10 || y >= boxY + boxHeight - 8) return ""
        return ocrRoi(Mat(bgr, Rect(x, y, w, h)), singleLine = false)
    }

    /**
     * OCR the DT / ÖT date line (below relationship, above Doğum Yeri).
     */
    fun readDateLineBand(
        bgr: Mat,
        boxX: Int,
        boxY: Int,
        boxWidth: Int,
        boxHeight: Int,
        isRoot: Boolean,
    ): String {
        val barCrop =
            if (isRoot) {
                0
            } else {
                maxOf(6, minOf(12, (boxWidth * 0.04).toInt()))
            }
        val padX = 1
        val x = (boxX + barCrop + padX).coerceIn(0, bgr.cols() - 1)
        val w = (boxWidth - barCrop - 2 * padX).coerceAtLeast(1).coerceAtMost(bgr.cols() - x)
        // Date line sits in the lower-middle of the card (after role, before place)
        val yRatio = if (isRoot) 0.48 else 0.55
        val hRatio = if (isRoot) 0.22 else 0.20
        val y = (boxY + (boxHeight * yRatio).toInt()).coerceIn(0, bgr.rows() - 1)
        val h =
            (boxHeight * hRatio).toInt()
                .coerceAtLeast(14)
                .coerceAtMost(bgr.rows() - y)
                .coerceAtMost(boxY + boxHeight - y)

        if (h < 10) return ""
        return ocrRoi(Mat(bgr, Rect(x, y, w, h)), mode = OcrMode.DATE_LINE)
    }

    /**
     * OCR the last line: Doğum Yeri:… (below DT/ÖT).
     */
    fun readPlaceLineBand(
        bgr: Mat,
        boxX: Int,
        boxY: Int,
        boxWidth: Int,
        boxHeight: Int,
        isRoot: Boolean,
    ): String {
        val barCrop =
            if (isRoot) {
                0
            } else {
                maxOf(6, minOf(12, (boxWidth * 0.04).toInt()))
            }
        val padX = 1
        val x = (boxX + barCrop + padX).coerceIn(0, bgr.cols() - 1)
        val w = (boxWidth - barCrop - 2 * padX).coerceAtLeast(1).coerceAtMost(bgr.cols() - x)
        val yRatio = if (isRoot) 0.68 else 0.72
        val hRatio = if (isRoot) 0.26 else 0.24
        val y = (boxY + (boxHeight * yRatio).toInt()).coerceIn(0, bgr.rows() - 1)
        val h =
            (boxHeight * hRatio).toInt()
                .coerceAtLeast(12)
                .coerceAtMost(bgr.rows() - y)
                .coerceAtMost(boxY + boxHeight - y - 1)

        if (h < 8) return ""
        return ocrRoi(Mat(bgr, Rect(x, y, w, h)), mode = OcrMode.PLACE_LINE)
    }

    private enum class OcrMode {
        BLOCK,
        NAME_LINE,
        DATE_LINE,
        PLACE_LINE,
    }

    private fun ocrRoi(
        roi: Mat,
        singleLine: Boolean,
    ): String = ocrRoi(roi, if (singleLine) OcrMode.NAME_LINE else OcrMode.BLOCK)

    private fun ocrRoi(
        roi: Mat,
        mode: OcrMode,
    ): String {
        val enhanced =
            when (mode) {
                OcrMode.NAME_LINE, OcrMode.DATE_LINE, OcrMode.PLACE_LINE ->
                    ImagePreprocessor.enhanceForNameLineOcr(roi)
                OcrMode.BLOCK -> ImagePreprocessor.enhanceForOcr(roi)
            }
        val pngBytes = matToPngBytes(enhanced)

        val pix: PIX? = leptonica.pixReadMem(pngBytes, pngBytes.size.toLong())
        if (pix == null || pix.isNull) {
            throw IllegalStateException("Failed to decode image for OCR")
        }

        return try {
            synchronized(api) {
                when (mode) {
                    OcrMode.NAME_LINE -> {
                        api.SetPageSegMode(tesseract.PSM_SINGLE_LINE)
                        api.SetVariable("user_defined_dpi", "400")
                        api.SetVariable(
                            "tessedit_char_whitelist",
                            "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZXabcçdefgğhıijklmnoöprsştuüvyzx ",
                        )
                    }
                    OcrMode.DATE_LINE -> {
                        api.SetPageSegMode(tesseract.PSM_SINGLE_LINE)
                        api.SetVariable("user_defined_dpi", "400")
                        api.SetVariable(
                            "tessedit_char_whitelist",
                            "0123456789DTÖOdtöot .:/-",
                        )
                    }
                    OcrMode.PLACE_LINE -> {
                        api.SetPageSegMode(tesseract.PSM_SINGLE_LINE)
                        api.SetVariable("user_defined_dpi", "400")
                        // Label + place letters (multi-word places allowed)
                        api.SetVariable(
                            "tessedit_char_whitelist",
                            "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZabcçdefgğhıijklmnoöprsştuüvyz :.-/",
                        )
                    }
                    OcrMode.BLOCK -> {
                        api.SetPageSegMode(tesseract.PSM_SINGLE_BLOCK)
                        api.SetVariable("user_defined_dpi", "300")
                        api.SetVariable("tessedit_char_whitelist", "")
                    }
                }
                api.SetImage(pix)
                val textPtr: BytePointer? = api.GetUTF8Text()
                if (textPtr == null || textPtr.isNull) {
                    ""
                } else {
                    try {
                        textPtr.string?.trim().orEmpty()
                    } finally {
                        textPtr.deallocate()
                    }
                }
            }
        } finally {
            leptonica.pixDestroy(pix)
        }
    }

    private fun matToPngBytes(mat: Mat): ByteArray {
        val bytes = MatOfByte()
        Imgcodecs.imencode(".png", mat, bytes)
        return bytes.toArray()
    }
}
