package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH_NAME
import com.darkrockstudios.apps.hammer.project.ProjectRepository
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
    val projectRepository: ProjectRepository = get()

    post("/create") {
        val formParameters = call.receiveParameters()
        val email = formParameters["email"].toString()
        val password = formParameters["password"].toString()
        val installId = formParameters["installId"].toString()

        val result = accountsRepository.createAccount(email = email, installId = installId, password = password)
        if (result.isSuccess) {
            val token = result.getOrThrow()
            projectRepository.createUserData(token.userId)

            call.respond(HttpStatusCode.Created, token)
        } else {
            val message = result.exceptionOrNull()?.message
            val response = HttpResponseError(error = "Failed to create account", message = message ?: "Unknown error")
            call.respond(status = HttpStatusCode.Conflict, response)
        }
    }
}

private fun Route.login() {
    val accountsRepository: AccountsRepository = get()

    post("/login") {
        val formParameters = call.receiveParameters()
        val email = formParameters["email"].toString()
        val password = formParameters["password"].toString()
        val installId = formParameters["installId"].toString()

        val result = accountsRepository.login(email = email, password = password, installId = installId)
        if (result.isSuccess) {
            val authToken = result.getOrThrow()
            call.respond(authToken)
        } else {
            val message = result.exceptionOrNull()?.message
            val response = HttpResponseError(error = "Failed to authenticate", message = message ?: "Unknown error")
            call.respond(status = HttpStatusCode.Unauthorized, response)
        }
    }
}

private fun Route.refreshToken() {
    val accountsRepository: AccountsRepository = get()

    post("/refresh_token/{userId}") {
        val userId = call.parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID

        val formParameters = call.receiveParameters()
        val installId = formParameters["installId"].toString()
        val refreshToken = formParameters["refreshToken"].toString()

        val result =
            accountsRepository.refreshToken(userId = userId, installId = installId, refreshToken = refreshToken)
        if (result.isSuccess) {
            val token = result.getOrThrow()
            call.respond(token)
        } else {
            call.respondText("Failed to refresh auth token", status = HttpStatusCode.Unauthorized)
        }
    }
}

private fun Route.testAuth() {
    get("/test_auth/{userId}") {
        val principal = call.principal<ServerUserIdPrincipal>()!!
        call.respondText("Authenticated as ${principal.id}")
    }
}
