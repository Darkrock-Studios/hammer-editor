package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*

class ServerAdminApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun getWhiteList(): Result<List<String>> {
		return get(
			"/api/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}

	suspend fun addToWhiteList(email: String): Result<String> {
		return put(
			"/api/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}

	suspend fun removeFromWhiteList(email: String): Result<String> {
		return delete(
			"/api/admin/$userId/whitelist",
			parse = { it.body() }
		)
	}
}
