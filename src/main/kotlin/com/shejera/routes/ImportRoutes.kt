package com.shejera.routes

import com.shejera.api.BadRequestException
import com.shejera.services.ImportService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory

fun Route.importRoutes(importService: ImportService) {
    val log = LoggerFactory.getLogger("com.shejera.routes.ImportRoutes")

    route("/imports") {
        get("/status") {
            call.respond(importService.status())
        }

        post("/upload") {
            log.info("[import] POST /imports/upload received")
            val multipart = call.receiveMultipart()
            var fileName: String? = null
            var contentType: String? = null
            var bytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        if (part.name == "file" || fileName == null) {
                            fileName = part.originalFileName ?: "upload.bin"
                            contentType = part.contentType?.toString()
                            bytes = part.provider().readRemaining().readByteArray()
                        }
                    }
                    else -> Unit
                }
                part.release()
            }

            val resolvedFileName = fileName
            val resolvedBytes = bytes
            if (resolvedFileName == null || resolvedBytes == null) {
                throw BadRequestException("multipart field 'file' is required")
            }

            val response =
                importService.upload(resolvedFileName, contentType, resolvedBytes)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/scan") {
            log.info("[import] POST /imports/scan received")
            call.respond(importService.scan())
            log.info("[import] POST /imports/scan responded")
        }

        get("/preview") {
            log.info("[import] GET /imports/preview")
            call.respond(importService.preview())
        }
    }
}
