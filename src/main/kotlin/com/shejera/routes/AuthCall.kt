package com.shejera.routes

import com.shejera.api.BadRequestException
import com.shejera.api.ForbiddenException
import com.shejera.auth.AuthPrincipal
import com.shejera.services.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.util.AttributeKey
import java.time.OffsetDateTime
import java.util.UUID

val AuthPrincipalKey = AttributeKey<AuthPrincipal>("authPrincipal")
val AuthServiceKey = AttributeKey<AuthService>("authService")

const val SESSION_COOKIE = "shejera_session"
const val TREE_HEADER = "X-Shejera-Tree-Id"

fun ApplicationCall.authService(): AuthService = application.attributes[AuthServiceKey]

fun ApplicationCall.principalOrNull(): AuthPrincipal? = attributes.getOrNull(AuthPrincipalKey)

fun ApplicationCall.requirePrincipal(): AuthPrincipal =
    principalOrNull() ?: authService().requirePrincipal(rawSessionToken())

/** Requires an authenticated admin principal; otherwise 403. */
fun ApplicationCall.requireAdmin(): AuthPrincipal {
    val principal = requirePrincipal()
    if (!principal.isAdmin) throw ForbiddenException("Admin access required")
    return principal
}

fun ApplicationCall.rawSessionToken(): String? =
    request.cookies[SESSION_COOKIE]
        ?: request.header(HttpHeaders.Authorization)
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

fun ApplicationCall.requestedTreeId(): UUID? {
    val raw = request.header(TREE_HEADER)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        UUID.fromString(raw)
    } catch (_: Exception) {
        throw BadRequestException("Invalid $TREE_HEADER")
    }
}

fun ApplicationCall.resolveTreeId(principal: AuthPrincipal): UUID =
    authService().resolveTreeId(principal, requestedTreeId())

fun ApplicationCall.setSessionCookie(
    token: String,
    expiresAt: OffsetDateTime,
) {
    val maxAge =
        (expiresAt.toEpochSecond() - OffsetDateTime.now().toEpochSecond())
            .coerceAtLeast(0)
    response.cookies.append(
        name = SESSION_COOKIE,
        value = token,
        maxAge = maxAge,
        path = "/",
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )
}

fun ApplicationCall.clearSessionCookie() {
    response.cookies.append(
        name = SESSION_COOKIE,
        value = "",
        maxAge = 0L,
        path = "/",
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )
}

fun parseUuidParam(value: String?): UUID =
    try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw BadRequestException("Invalid UUID: $value")
    }
