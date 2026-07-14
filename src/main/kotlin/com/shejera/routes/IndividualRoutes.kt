package com.shejera.routes

import com.shejera.api.BadRequestException
import com.shejera.models.CreateIndividualEventRequest
import com.shejera.models.CreateIndividualRequest
import com.shejera.models.UpdateIndividualRequest
import com.shejera.services.IndividualService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.individualRoutes(individualService: IndividualService) {
    route("/individuals") {
        get {
            call.respond(individualService.list())
        }

        post {
            val request = call.receive<CreateIndividualRequest>()
            val created = individualService.create(request)
            call.respond(HttpStatusCode.Created, created)
        }

        get("/{id}") {
            val id = parseUuid(call.parameters["id"])
            call.respond(individualService.get(id))
        }

        put("/{id}") {
            val id = parseUuid(call.parameters["id"])
            val request = call.receive<UpdateIndividualRequest>()
            call.respond(individualService.update(id, request))
        }

        delete("/{id}") {
            val id = parseUuid(call.parameters["id"])
            individualService.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }

        get("/{id}/relationships") {
            val id = parseUuid(call.parameters["id"])
            call.respond(individualService.getRelationships(id))
        }

        get("/{id}/events") {
            val id = parseUuid(call.parameters["id"])
            call.respond(individualService.listEvents(id))
        }

        post("/{id}/events") {
            val id = parseUuid(call.parameters["id"])
            val request = call.receive<CreateIndividualEventRequest>()
            val event = individualService.addEvent(id, request)
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
