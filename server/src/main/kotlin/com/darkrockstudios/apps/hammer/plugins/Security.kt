package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.AUTH_REALM
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.koin.ktor.ext.inject

const val USER_AUTH_NAME = "UserAuth"

fun Application.configureSecurity() {
    val accountRepo: AccountsRepository by inject()

    authentication {
        bearer(
            name = USER_AUTH_NAME,
        ) {
            realm = AUTH_REALM
            authenticate { tokenCredential ->
                val result = accountRepo.checkToken(tokenCredential.token)
                if (result.isSuccess) {
                    val userEmail = result.getOrThrow()
                    UserIdPrincipal(userEmail)

                } else {
                    null
                }
            }
        }
    }
}
