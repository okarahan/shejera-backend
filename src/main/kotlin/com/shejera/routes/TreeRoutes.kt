package com.shejera.routes

import com.shejera.models.CreateContributionTreeRequest
import com.shejera.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.treeRoutes(authService: AuthService) {
    route("/trees") {
        get {
            val principal = call.requirePrincipal()
            call.respond(authService.listTrees(principal))
        }

        post("/contributions") {
            val principal = call.requirePrincipal()
            val body = call.receive<CreateContributionTreeRequest>()
            val created = authService.createContributionTree(principal, body)
            call.respond(HttpStatusCode.Created, created)
        }

        post("/{id}/submit") {
            val principal = call.requirePrincipal()
            val id = parseUuidParam(call.parameters["id"])
            call.respond(authService.submitContribution(principal, id))
        }

        delete("/{id}") {
            val principal = call.requirePrincipal()
            val id = parseUuidParam(call.parameters["id"])
            authService.discardContribution(principal, id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
