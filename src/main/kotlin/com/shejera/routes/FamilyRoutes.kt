package com.shejera.routes

import com.shejera.api.BadRequestException
import com.shejera.models.AddChildRequest
import com.shejera.models.CreateFamilyEventRequest
import com.shejera.models.CreateFamilyRequest
import com.shejera.services.FamilyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.familyRoutes(familyService: FamilyService) {
    route("/families") {
        get {
            call.respond(familyService.list())
        }

        post {
            val request = call.receive<CreateFamilyRequest>()
            val created = familyService.create(request)
            call.respond(HttpStatusCode.Created, created)
        }

        get("/{id}") {
            val id = parseUuid(call.parameters["id"])
            call.respond(familyService.get(id))
        }

        delete("/{id}") {
            val id = parseUuid(call.parameters["id"])
            familyService.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{id}/children") {
            val id = parseUuid(call.parameters["id"])
            val request = call.receive<AddChildRequest>()
            val child = familyService.addChild(id, request)
            call.respond(HttpStatusCode.Created, child)
        }

        post("/{id}/events") {
            val id = parseUuid(call.parameters["id"])
            val request = call.receive<CreateFamilyEventRequest>()
            val event = familyService.addEvent(id, request)
            call.respond(HttpStatusCode.Created, event)
        }
    }
}

private fun parseUuid(value: String?): UUID =
    try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw BadRequestException("Invalid UUID: $value")
    }
