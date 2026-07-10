package com.shejera.routes

import com.shejera.plugins.dataSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val status: String,
)

fun Route.healthRoutes() {
    get("/") {
        call.respondText("shejera ok")
    }

    get("/health") {
        call.respond(StatusResponse(status = "ok"))
    }

    get("/ready") {
        val isReady =
            runCatching {
                call.application.dataSource().connection.use { connection ->
                    connection.isValid(2)
                }
            }.getOrDefault(false)

        if (isReady) {
            call.respond(StatusResponse(status = "ready"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, StatusResponse(status = "not_ready"))
        }
    }
}
