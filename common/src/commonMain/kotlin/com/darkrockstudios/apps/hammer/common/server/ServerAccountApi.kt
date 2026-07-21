package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HTTP_STATUS_TERMS_OF_SERVICE
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.ContentConvertException

class ServerAccountApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	strRes: StrRes
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun createAccount(
		email: String,
		password: String,
		installId: String,
		acceptedTosVersion: String? = null,
	): Result<Token> {
		return post(
			"/api/account/create",
			parse = { it.body() },
			failureHandler = { response -> createAccountFailure(response) },
		) {
			setBody(
				FormDataContent(
					Parameters.build {
						append("email", email)
						append("password", password)
						append("installId", installId)
						acceptedTosVersion?.let { append("acceptedTosVersion", it) }
					}
				)
			)
		}
	}

	private suspend fun createAccountFailure(response: HttpResponse): Throwable {
		if (response.status.value == HTTP_STATUS_TERMS_OF_SERVICE) {
			val challenge = try {
				response.body<TermsOfServiceChallenge>()
			} catch (e: NoTransformationFoundException) {
				null
			} catch (e: ContentConvertException) {
				// A 451 with a malformed/empty body falls through to the default failure.
				null
			}
			if (challenge != null) return TermsOfServiceRequiredException(challenge)
		}
		return defaultFailure(response)
	}

	suspend fun login(
		email: String,
		password: String,
		installId: String,
	): Result<Token> {
		return post("/api/account/login/", parse = { it.body() }) {
			setBody(
				FormDataContent(
					Parameters.build {
						append("email", email)
						append("password", password)
						append("installId", installId)
					}
				)
			)
		}
	}

	suspend fun testAuth(): Result<String> {
		return get("/api/account/test_auth/$userId")
	}
}