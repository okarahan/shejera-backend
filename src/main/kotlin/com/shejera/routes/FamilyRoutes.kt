package com.shejera.routes

import com.shejera.models.AddChildRequest
import com.shejera.models.CreateFamilyEventRequest
import com.shejera.models.CreateFamilyRequest
import com.shejera.services.AuthService
import com.shejera.services.FamilyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.familyRoutes(
    familyService: FamilyService,
    authService: AuthService,
) {
    route("/families") {
        get {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            call.respond(familyService.list(treeId))
        }

        post {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val request = call.receive<CreateFamilyRequest>()
            val created = familyService.create(request, treeId)
            call.respond(HttpStatusCode.Created, created)
        }

        get("/{id}") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            val id = parseUuidParam(call.parameters["id"])
            call.respond(familyService.get(id, treeId))
        }

        delete("/{id}") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            familyService.delete(id, treeId)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{id}/children") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            val request = call.receive<AddChildRequest>()
            val child = familyService.addChild(id, request, treeId)
            call.respond(HttpStatusCode.Created, child)
        }

        post("/{id}/events") {
            val principal = call.requirePrincipal()
            val treeId = call.resolveTreeId(principal)
            authService.requireWrite(principal, treeId)
            val id = parseUuidParam(call.parameters["id"])
            val request = call.receive<CreateFamilyEventRequest>()
            val event = familyService.addEvent(id, request, treeId)
            call.respond(HttpStatusCode.Created, event)
        }
    }
}
