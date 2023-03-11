package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.accountRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/teapot") {
            call.respondText("I'm a little Tea Pot", status = HttpStatusCode.fromValue(418))
        }
    }

    accountRoutes()
}
