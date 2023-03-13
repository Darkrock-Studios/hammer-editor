package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

class ServerAccountApi(
    httpClient: HttpClient,
    globalSettingsRepository: GlobalSettingsRepository
) : Api(httpClient, globalSettingsRepository) {

    suspend fun createAccount(
        email: String,
        password: String,
        deviceId: String,
    ): Result<Token> {
        return post("/account/create", { it.body() }) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("email", email)
                        append("password", password)
                        append("deviceId", deviceId)
                    }
                )
            )
        }
    }

    suspend fun login(
        email: String,
        password: String,
        deviceId: String,
    ): Result<Token> {
        return post("/account/login/", { it.body() }) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("email", email)
                        append("password", password)
                        append("deviceId", deviceId)
                    }
                )
            )
        }
    }

    suspend fun testAuth(): Result<String> {
        return get("/account/test_auth/")
    }
}