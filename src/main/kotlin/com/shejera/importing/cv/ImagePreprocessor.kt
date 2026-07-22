package com.shejera.importing.cv

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.file.Path

object ImagePreprocessor {
    fun loadBgr(imagePath: Path): Mat {
        val image = Imgcodecs.imread(imagePath.toAbsolutePath().toString())
        if (image.empty()) {
            throw IllegalArgumentException("Could not read image: $imagePath")
        }
        return image
    }

    fun toHsv(bgr: Mat): Mat {
        val hsv = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
        return hsv
    }

    fun toGray(bgr: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    /**
     * Removes faint gray logo watermarks (low saturation, light gray wash) by
     * whitening them. Keeps dark ink, colored gender bars, yellow highlights,
     * and the pale cyan card body fill (needed to bound boxes against white).
     */
    fun suppressGrayWatermark(bgr: Mat): Mat {
        val out = bgr.clone()
        val hsv = toHsv(bgr)
        val watermark = Mat()
        // Light gray wash / logo tint
        Core.inRange(
            hsv,
            Scalar(0.0, 0.0, 200.0),
            Scalar(180.0, 60.0, 252.0),
            watermark,
        )
        // Preserve pale cyan/blue card fill (hue ~106, sat ~15) — not watermark
        val cardBody = Mat()
        Core.inRange(
            hsv,
            Scalar(85.0, 5.0, 200.0),
            Scalar(125.0, 55.0, 255.0),
            cardBody,
        )
        val notCard = Mat()
        Core.bitwise_not(cardBody, notCard)
        Core.bitwise_and(watermark, notCard, watermark)
        out.setTo(Scalar(255.0, 255.0, 255.0), watermark)
        return out
    }

    /** Force light gray (watermark / paper tint) to white; keep dark ink. */
    fun whitenNonInk(
        gray: Mat,
        inkMaxLuminance: Double = 175.0,
    ): Mat {
        val cleaned = gray.clone()
        val light = Mat()
        Imgproc.threshold(gray, light, inkMaxLuminance, 255.0, Imgproc.THRESH_BINARY)
        cleaned.setTo(Scalar(255.0), light)
        return cleaned
    }

    fun enhanceForOcr(roiBgr: Mat): Mat {
        val gray = toGray(roiBgr)
        val scaled = Mat()
        val scale =
            when {
                roiBgr.width() < 120 -> 3.0
                roiBgr.width() < 220 -> 2.0
                else -> 1.5
            }
        Imgproc.resize(gray, scaled, Size(gray.width() * scale, gray.height() * scale))
        // Soft watermark leftovers inside a card ROI → white before Otsu
        val cleaned = whitenNonInk(scaled, inkMaxLuminance = 175.0)
        val binary = Mat()
        Imgproc.threshold(cleaned, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        return binary
    }

    /**
     * Name-line OCR prep: upscale strongly, light cleanup, soft binarize.
     * Hard Otsu on bold H often breaks the crossbar → Tesseract reads "L".
     */
    fun enhanceForNameLineOcr(roiBgr: Mat): Mat {
        val gray = toGray(roiBgr)
        val scale =
            when {
                roiBgr.height() < 28 -> 4.0
                roiBgr.height() < 40 -> 3.5
                else -> 3.0
            }
        val scaled = Mat()
        Imgproc.resize(
            gray,
            scaled,
            Size((gray.width() * scale).toInt().toDouble(), (gray.height() * scale).toInt().toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_CUBIC,
        )
        val cleaned = whitenNonInk(scaled, inkMaxLuminance = 190.0)
        // Mild blur keeps H crossbar connected after threshold
        val blurred = Mat()
        Imgproc.GaussianBlur(cleaned, blurred, Size(3.0, 3.0), 0.0)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            31,
            8.0,
        )
        return binary
    }
}
