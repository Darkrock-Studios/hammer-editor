package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.plugins.*
import com.darkrockstudios.apps.hammer.plugins.kweb.configureKweb
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType

fun main(args: Array<String>) {
    val parser = ArgParser("server")
    val portArg by parser.option(ArgType.Int, shortName = "p", fullName = "", description = "Port")

    parser.parse(args)

    val port = portArg ?: 8080

    embeddedServer(
        Netty,
        port = port,
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
    configureKweb()
}
