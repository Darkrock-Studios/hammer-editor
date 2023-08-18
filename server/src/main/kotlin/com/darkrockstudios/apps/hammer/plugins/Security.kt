package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.koin.ktor.ext.inject

const val USER_AUTH = "UserAuth"
const val ADMIN_AUTH = "AdminAuth"

fun Application.configureSecurity() {
	val accountRepo: AccountsRepository by inject()
	val whitelistRepo: WhiteListRepository by inject()

	authentication {
		bearer(name = USER_AUTH) {
			realm = AUTH_REALM
			authenticate { tokenCredential ->
				val userId = parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID
				val result = accountRepo.checkToken(userId, tokenCredential.token)
				if (isSuccess(result)) {
					val okay = if (whitelistRepo.useWhiteList()) {
						val account = accountRepo.getAccount(userId)
						account.isAdmin || whitelistRepo.isOnWhiteList(account.email)
					} else {
						true
					}

					val dbUserId = result.data
					ServerUserIdPrincipal(dbUserId)

					if (okay) {
						ServerUserIdPrincipal(dbUserId)
					} else {
						null
					}
				} else {
					null
				}
			}
		}
		admin(name = ADMIN_AUTH)
	}
}

data class ServerUserIdPrincipal(val id: Long) : Principal