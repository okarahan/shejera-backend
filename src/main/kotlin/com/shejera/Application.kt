package com.shejera

import com.shejera.plugins.configureDatabase
import com.shejera.plugins.configureMonitoring
import com.shejera.plugins.configureRouting
import com.shejera.plugins.configureSerialization
import com.shejera.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureDatabase()
    configureRouting()
}
