package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.parameter

class ServerProjectsApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
	strRes: StrRes,
) : Api(httpClient, globalSettingsRepository, strRes) {

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

	suspend fun deleteProject(projectId: ProjectId, syncId: String): Result<String> {
		return get(
			path = "/api/projects/$userId/delete",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", projectId.id)
			}
		)
	}

	suspend fun renameProject(
		projectId: ProjectId,
		syncId: String,
		newName: String
	): Result<String> {
		return get(
			path = "/api/projects/$userId/rename",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", projectId.id)
				parameter("projectName", newName)
			}
		)
	}

	suspend fun createProject(
		projectName: String,
		syncId: String,
	): Result<CreateProjectResponse> {
		return get(
			path = "/api/projects/$userId/$projectName/create",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			},
			parse = { it.body() },
		)
	}
}