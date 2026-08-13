package com.shejera.config

import io.ktor.server.config.ApplicationConfig

data class SmtpConfig(
    val host: String?,
    val port: Int,
    val user: String?,
    val password: String,
    val from: String?,
    val startTls: Boolean,
) {
    /** Ready to send when host and a From address (or user as fallback) exist. */
    val configured: Boolean
        get() = !host.isNullOrBlank() && !(from ?: user).isNullOrBlank()

    val fromAddress: String?
        get() = from?.takeIf { it.isNotBlank() } ?: user?.takeIf { it.isNotBlank() }

    companion object {
        fun empty(): SmtpConfig =
            SmtpConfig(
                host = null,
                port = 587,
                user = null,
                password = "",
                from = null,
                startTls = true,
            )

        fun from(config: ApplicationConfig): SmtpConfig {
            val smtp = config.config("shejera.smtp")
            return SmtpConfig(
                host = smtp.optionalString("host"),
                port = smtp.propertyOrNull("port")?.getString()?.toIntOrNull() ?: 587,
                user = smtp.optionalString("user"),
                password = smtp.propertyOrNull("password")?.getString().orEmpty(),
                from = smtp.optionalString("from"),
                startTls =
                    smtp.propertyOrNull("startTls")?.getString()?.trim()?.lowercase()
                        !in setOf("0", "false", "no"),
            )
        }

        private fun ApplicationConfig.optionalString(path: String): String? =
            propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
