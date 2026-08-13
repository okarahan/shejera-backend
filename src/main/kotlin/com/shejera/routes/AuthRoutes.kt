package com.shejera.routes

import com.shejera.models.ApproveInviteRequestBody
import com.shejera.models.CreateInviteRequest
import com.shejera.models.RedeemInviteRequest
import com.shejera.models.RequestInviteRequest
import com.shejera.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        get("/invite/{token}") {
            val token = call.parameters["token"] ?: ""
            call.respond(authService.previewInvite(token))
        }

        post("/redeem") {
            val body = call.receive<RedeemInviteRequest>()
            val result = authService.redeemInvite(body.token.trim())
            call.setSessionCookie(result.sessionToken, result.expiresAt)
            call.respond(authService.me(result.principal))
        }

        post("/logout") {
            authService.logout(call.rawSessionToken())
            call.clearSessionCookie()
            call.respond(HttpStatusCode.NoContent)
        }

        get("/me") {
            val principal = call.requirePrincipal()
            call.respond(authService.me(principal))
        }
    }

    /** Public: ask for an invite (admin must approve). */
    post("/invite-requests") {
        val body = call.receive<RequestInviteRequest>()
        call.respond(HttpStatusCode.Created, authService.requestInvite(body))
    }

    route("/invite-requests") {
        get {
            val principal = call.requirePrincipal()
            call.respond(authService.listInviteRequests(principal))
        }

        post("/{id}/approve") {
            val principal = call.requirePrincipal()
            val body =
                runCatching { call.receive<ApproveInviteRequestBody>() }
                    .getOrElse { ApproveInviteRequestBody() }
            val result =
                authService.approveInviteRequest(
                    principal,
                    parseUuidParam(call.parameters["id"]),
                    body,
                )
            call.respond(result)
        }

        post("/{id}/reject") {
            val principal = call.requirePrincipal()
            authService.rejectInviteRequest(principal, parseUuidParam(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/invites") {
        get {
            val principal = call.requirePrincipal()
            call.respond(authService.listInvites(principal))
        }

        post {
            val principal = call.requirePrincipal()
            val body = call.receive<CreateInviteRequest>()
            val created = authService.createInvite(principal, body)
            call.respond(HttpStatusCode.Created, created)
        }

        delete("/{id}") {
            val principal = call.requirePrincipal()
            authService.revokeInvite(principal, parseUuidParam(call.parameters["id"]))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
