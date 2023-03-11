package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::appMain,
        watchPaths = listOf("classes")
    ).start(wait = true)
}

fun Application.appMain() {
    configureDependencyInjection()
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureSecurity()
    configureRouting()
}
