package com.shejera.importing.cv

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/**
 * Provides a filesystem tessdata directory.
 * Language models (eng.traineddata, tur.traineddata) are bundled on the classpath
 * under tessdata/ via the Gradle downloadTessdata task and extracted on first use.
 */
object TessdataBundle {
    private val log = LoggerFactory.getLogger(TessdataBundle::class.java)
    private val cachedPath = AtomicReference<Path?>(null)
    private val languages = listOf("eng", "tur")

    fun resolveDataPath(overridePath: String?): String {
        if (!overridePath.isNullOrBlank()) {
            log.info("[cv] using override tessdata path={}", overridePath)
            return Path.of(overridePath).toAbsolutePath().normalize().toString()
        }

        cachedPath.get()?.let { return it.toString() }

        synchronized(this) {
            cachedPath.get()?.let { return it.toString() }

            val dir =
                Path.of(System.getProperty("java.io.tmpdir"), "shejera-tessdata")
                    .toAbsolutePath()
                    .normalize()
            Files.createDirectories(dir)
            log.info("[cv] preparing bundled tessdata at {}", dir)

            for (lang in languages) {
                val target = dir.resolve("$lang.traineddata")
                if (Files.exists(target) && Files.size(target) > 0L) {
                    log.info("[cv] tessdata {} already present ({} bytes)", lang, Files.size(target))
                    continue
                }

                val resource = "/tessdata/$lang.traineddata"
                log.info("[cv] extracting {} …", resource)
                val stream =
                    TessdataBundle::class.java.getResourceAsStream(resource)
                        ?: throw IllegalStateException(
                            "Missing bundled Tesseract model $resource. " +
                                "Run Gradle task downloadTessdata.",
                        )
                stream.use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
                log.info("[cv] extracted {} ({} bytes)", lang, Files.size(target))
            }

            cachedPath.set(dir)
            return dir.toString()
        }
    }
}
