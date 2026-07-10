package com.shejera.config

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseConfig =
            DatabaseConfig(
                jdbcUrl = config.property("database.jdbcUrl").getString(),
                user = config.property("database.user").getString(),
                password = config.property("database.password").getString(),
            )
    }
}
