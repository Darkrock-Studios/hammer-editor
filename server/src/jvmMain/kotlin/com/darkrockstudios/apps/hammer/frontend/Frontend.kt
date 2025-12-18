package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.frontend() {
	val accountsRepository: AccountsRepository by inject()
	val whiteListRepository: WhiteListRepository by inject()

	staticResources("/assets", "/assets")

	homeRoutes()
	localeRoutes()
	authRoutes(accountsRepository)
	adminRoutes(whiteListRepository)
}

fun Application.configureFrontEnd() {
	configureTemplating()

	install(plugin = Sessions) {
		cookie<UserSession>("user_session") {
			cookie.path = "/"
			cookie.maxAgeInSeconds = 3600 * 24 * 7 // 7 days
			cookie.extensions["SameSite"] = "lax"
		}
	}
}

fun AuthenticationConfig.frontendAuthentication(accountRepo: AccountsRepository, whitelistRepo: WhiteListRepository) {
	session<UserSession>("auth-session") {
		validate { session ->
			// Check if it's valid, eventually...
			session
		}
		challenge {
			call.respondRedirect("/login")
		}
	}
}