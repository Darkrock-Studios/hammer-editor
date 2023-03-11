package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.plugins.USER_AUTH_NAME
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.accountRoutes() {
    routing {
        route("/account") {
            createAccount()
            login()
            refreshToken()
            authenticate(USER_AUTH_NAME) {
                testAuth()
            }
        }
    }
}

private fun Route.createAccount() {
    val accountsRepository: AccountsRepository = get()

    post("/create") {
        val formParameters = call.receiveParameters()
        val email = formParameters["email"].toString()
        val password = formParameters["password"].toString()
        val deviceId = formParameters["deviceId"].toString()

        val result = accountsRepository.createAccount(email = email, deviceId = deviceId, password = password)
        if (result.isSuccess) {
            val token = result.getOrThrow()
            call.respond(HttpStatusCode.Created, token)
        } else {
            call.respondText("Failed to create account", status = HttpStatusCode.Conflict)
        }
    }
}

private fun Route.login() {
    val accountsRepository: AccountsRepository = get()

    post("/login") {
        val formParameters = call.receiveParameters()
        val email = formParameters["email"].toString()
        val password = formParameters["password"].toString()
        val deviceId = formParameters["deviceId"].toString()

        val result = accountsRepository.login(email = email, password = password, deviceId = deviceId)
        if (result.isSuccess) {
            val authToken = result.getOrThrow()
            call.respond(authToken)
        } else {
            call.respondText("Failed to authenticate", status = HttpStatusCode.Unauthorized)
        }
    }
}

private fun Route.refreshToken() {
    val accountsRepository: AccountsRepository = get()

    post("/refresh_token") {
        val formParameters = call.receiveParameters()
        val deviceId = formParameters["deviceId"].toString()
        val refreshToken = formParameters["refreshToken"].toString()

        val result = accountsRepository.refreshToken(deviceId = deviceId, refreshToken = refreshToken)
        if (result.isSuccess) {
            val token = result.getOrThrow()
            call.respond(token)
        } else {
            call.respondText("Failed to refresh auth token", status = HttpStatusCode.Unauthorized)
        }
    }
}


private fun Route.testAuth() {
    get("/test_auth") {
        val principal = call.principal<UserIdPrincipal>()!!
        call.respondText("Authenticated as ${principal.name}")
    }
}
