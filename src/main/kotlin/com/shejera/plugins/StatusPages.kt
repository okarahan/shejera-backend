package com.shejera.plugins

import com.shejera.api.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException

@Serializable
data class ErrorResponse(
    val error: String,
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                status = HttpStatusCode.fromValue(cause.statusCode),
                message = ErrorResponse(error = cause.message ?: "Request failed"),
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse(error = cause.message ?: "Bad request"),
            )
        }

        exception<PSQLException> { call, cause ->
            val message =
                when (cause.sqlState) {
                    "23503" -> "Referenced record does not exist"
                    "23505" -> "Record already exists"
                    "23514" -> "Invalid value for constrained field"
                    else -> "Database error"
                }
            val status =
                when (cause.sqlState) {
                    "23505" -> HttpStatusCode.Conflict
                    else -> HttpStatusCode.BadRequest
                }
            call.respond(status = status, message = ErrorResponse(error = message))
        }
    }
}
