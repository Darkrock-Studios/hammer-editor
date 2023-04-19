package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*

class ServerAdminApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository
) : Api(httpClient, globalSettingsRepository) {

	suspend fun getWhiteList(): Result<List<String>> {
		return get(
			"/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}

	suspend fun addToWhiteList(email: String): Result<String> {
		return put(
			"/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}

	suspend fun removeFromWhiteList(email: String): Result<String> {
		return delete(
			"/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}
}
