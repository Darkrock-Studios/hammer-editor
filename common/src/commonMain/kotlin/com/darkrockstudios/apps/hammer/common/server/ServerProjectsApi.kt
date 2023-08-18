package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class ServerProjectsApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
) : Api(httpClient, globalSettingsRepository) {

	suspend fun beginProjectsSync(): Result<BeginProjectsSyncResponse> {
		return get(
			path = "/api/projects/$userId/begin_sync",
			parse = { it.body() },
		)
	}

	suspend fun endProjectsSync(syncId: String): Result<String> {
		return get(
			path = "/api/projects/$userId/end_sync",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}

	suspend fun deleteProject(projectName: String, syncId: String): Result<String> {
		return get(
			path = "/api/projects/$userId/$projectName/delete",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}

	suspend fun createProject(projectName: String, syncId: String): Result<String> {
		return get(
			path = "/api/projects/$userId/$projectName/create",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}
}