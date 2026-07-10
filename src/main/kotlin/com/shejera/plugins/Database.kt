package com.shejera.plugins

import com.shejera.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.util.AttributeKey
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

val DataSourceKey = AttributeKey<DataSource>("dataSource")
val DslContextKey = AttributeKey<DSLContext>("dslContext")

fun Application.configureDatabase() {
    val config = DatabaseConfig.from(environment.config)

    val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                poolName = "shejera"
            },
        )

    Flyway
        .configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    val dsl = DSL.using(dataSource, SQLDialect.POSTGRES)

    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }

    attributes.put(DataSourceKey, dataSource)
    attributes.put(DslContextKey, dsl)
}

fun Application.dataSource(): DataSource = attributes[DataSourceKey]

fun Application.dsl(): DSLContext = attributes[DslContextKey]
