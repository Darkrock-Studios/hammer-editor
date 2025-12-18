package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
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
import kotlin.time.Duration.Companion.days

fun Route.frontend(config: ServerConfig) {
	val accountsRepository: AccountsRepository by inject()
	val whiteListRepository: WhiteListRepository by inject()

	staticResources("/assets", "/assets")

	homeRoutes(config, whiteListRepository)
	localeRoutes()
	authRoutes(accountsRepository)
	adminRoutes(config, whiteListRepository)
}

const val COOKIE_USER_SESSION = "user_session"

fun Application.configureFrontEnd() {
	configureTemplating()

	install(plugin = Sessions) {
		cookie<UserSession>(COOKIE_USER_SESSION) {
			cookie.path = "/"
			cookie.maxAgeInSeconds = 7.days.inWholeSeconds
			cookie.extensions["SameSite"] = "lax"
		}
	}
}

const val SESSION_AUTH = "auth-session"

fun AuthenticationConfig.frontendAuthentication(accountRepo: AccountsRepository, whitelistRepo: WhiteListRepository) {
	session<UserSession>(SESSION_AUTH) {
		validate { session ->
			val account = accountRepo.getAccount(session.userId)
			if (whitelistRepo.useWhiteList()) {
				whitelistRepo.isOnWhiteList(account.email)
			} else {
				true
			}
		}
		challenge {
			call.respondRedirect("/login")
		}
	}
}