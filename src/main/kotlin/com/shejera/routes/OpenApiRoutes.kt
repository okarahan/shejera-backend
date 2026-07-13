package com.shejera.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.openApiRoutes() {
    get("/openapi.yaml") {
        val spec =
            javaClass.classLoader.getResourceAsStream("openapi/openapi.yaml")?.use { stream ->
                stream.reader().readText()
            }
        if (spec == null) {
            call.respond(HttpStatusCode.NotFound, "OpenAPI spec not found")
        } else {
            call.respondText(spec, ContentType.parse("application/yaml"))
        }
    }
}
