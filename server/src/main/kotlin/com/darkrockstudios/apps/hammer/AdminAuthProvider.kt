package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AdminAuthProvider internal constructor(config: Config) : AuthenticationProvider(config), KoinComponent {

	private val accountsRepository: AccountsRepository by inject()

	override suspend fun onAuthenticate(context: AuthenticationContext) {
		val userId = context.call.parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID

		val isAdmin = accountsRepository.isAdmin(userId)
		if (isAdmin.not()) {
			context.error("admin", AuthenticationFailedCause.InvalidCredentials)
			context.call.respond(status = HttpStatusCode.Unauthorized, "User is not an Admin")
		}
	}

	class Config(name: String?) : AuthenticationProvider.Config(name) {
		internal fun build() = AdminAuthProvider(this)
	}
}

fun AuthenticationConfig.admin(name: String? = null) {
	val provider = AdminAuthProvider.Config(name).build()
	register(provider)
}