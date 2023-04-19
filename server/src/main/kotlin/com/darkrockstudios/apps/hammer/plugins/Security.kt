package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin
import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.koin.ktor.ext.inject

const val USER_AUTH = "UserAuth"
const val ADMIN_AUTH = "AdminAuth"

fun Application.configureSecurity() {
	val accountRepo: AccountsRepository by inject()

	authentication {
		bearer(name = USER_AUTH) {
			realm = AUTH_REALM
			authenticate { tokenCredential ->
				val userId = parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID

				val result = accountRepo.checkToken(userId, tokenCredential.token)
				if (result.isSuccess) {
					val dbUserId = result.getOrThrow()
					ServerUserIdPrincipal(dbUserId)
				} else {
					null
				}
			}
		}
		admin(name = ADMIN_AUTH)
	}
}

data class ServerUserIdPrincipal(val id: Long) : Principal