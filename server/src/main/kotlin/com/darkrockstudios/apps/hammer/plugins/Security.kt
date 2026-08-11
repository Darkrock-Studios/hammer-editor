package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import com.darkrockstudios.apps.hammer.frontend.frontendAuthentication
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds

const val USER_AUTH = "UserAuth"
const val ADMIN_AUTH = "AdminAuth"

fun Application.configureSecurity() {
	val accountRepo: AccountsRepository by inject()
	val whitelistRepo: WhiteListRepository by inject()
	val loginRateLimit: LoginRateLimitConfig by inject()

	install(RateLimit) {
		register(RateLimitName(LOGIN_RATE_LIMIT)) {
			rateLimiter(limit = loginRateLimit.limit, refillPeriod = loginRateLimit.refillPeriodSeconds.seconds)
			requestKey { call -> call.request.origin.remoteHost }
		}
	}

	authentication {
		bearer(name = USER_AUTH) {
			realm = AUTH_REALM
			authenticate { tokenCredential ->
				val userId = parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID
				val result = accountRepo.checkToken(userId, tokenCredential.token)
				if (isSuccess(result)) {
					// deleted_at is checked before the admin allowance so a
					// soft-deleted admin is locked out like anyone else.
					val account = accountRepo.getAccountOrNull(result.data)
					val okay = account != null &&
						account.deleted_at == null &&
						(account.is_admin || whitelistRepo.isOnWhiteList(account.email))

					if (okay) {
						ServerUserIdPrincipal(result.data)
					} else {
						null
					}
				} else {
					null
				}
			}
		}
		admin(name = ADMIN_AUTH)
		frontendAuthentication(accountRepo, whitelistRepo)
	}
}

data class ServerUserIdPrincipal(val id: Long) : Principal