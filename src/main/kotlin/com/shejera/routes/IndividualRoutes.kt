package com.shejera.routes

import com.shejera.models.CreateIndividualEventRequest
import com.shejera.models.CreateIndividualRequest
import com.shejera.models.UpdateIndividualRequest
import com.shejera.services.AuthService
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

fun Route.individualRoutes(
    individualService: IndividualService,
    authService: AuthService,
) {
    route("/individuals") {
        get {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            call.respond(individualService.list(treeId))
        }

        post {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val request = call.receive<CreateIndividualRequest>()
            val created = individualService.create(request, treeId)
            call.respond(HttpStatusCode.Created, created)
        }

        get("/{id}") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            val id = parseUuidParam(call.parameters["id"])
            call.respond(individualService.get(id, treeId))
        }

        put("/{id}") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            val request = call.receive<UpdateIndividualRequest>()
            call.respond(individualService.update(id, request, treeId))
        }

        delete("/{id}") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            individualService.delete(id, treeId)
            call.respond(HttpStatusCode.NoContent)
        }

        get("/{id}/relationships") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            val id = parseUuidParam(call.parameters["id"])
            call.respond(individualService.getRelationships(id, treeId))
        }

        get("/{id}/events") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            val id = parseUuidParam(call.parameters["id"])
            call.respond(individualService.listEvents(id, treeId))
        }

        post("/{id}/events") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            val request = call.receive<CreateIndividualEventRequest>()
            val event = individualService.addEvent(id, request, treeId)
            call.respond(HttpStatusCode.Created, event)
        }
    }
}
