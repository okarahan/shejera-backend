package com.shejera.routes

import com.shejera.api.BadRequestException
import com.shejera.models.ImportCommitRequest
import com.shejera.services.ImportCommitService
import com.shejera.services.ImportService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import java.util.UUID

fun Route.importRoutes(
    importService: ImportService,
    importCommitService: ImportCommitService,
) {
    val log = LoggerFactory.getLogger("com.shejera.routes.ImportRoutes")

    route("/imports") {
        get("/status") {
            val principal = call.requirePrincipal()
            call.respond(importService.status(principal.id))
        }

        post("/upload") {
            val principal = call.requirePrincipal()
            log.info("[import] POST /imports/upload user={}", principal.id)
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
                importService.upload(
                    principal.id,
                    resolvedFileName,
                    contentType,
                    resolvedBytes,
                )
            call.respond(HttpStatusCode.Created, response)
        }

        post("/scan") {
            val principal = call.requirePrincipal()
            log.info("[import] POST /imports/scan user={}", principal.id)
            call.respond(importService.scan(principal.id))
        }

        get("/preview") {
            val principal = call.requirePrincipal()
            call.respond(importService.preview(principal.id))
        }

        post("/commit") {
            val principal = call.requirePrincipal()
            val body =
                runCatching { call.receive<ImportCommitRequest>() }
                    .getOrElse { ImportCommitRequest() }
            val treeId = body.treeId?.let { UUID.fromString(it) }
            val response =
                importCommitService.commit(
                    principal = principal,
                    treeName = body.treeName,
                    expiresInDays = body.expiresInDays,
                    targetTreeId = treeId,
                )
            call.respond(HttpStatusCode.Created, response)
        }
    }
}
