package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeRequest
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeResponse
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ServerProjectsApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun beginProjectsSync(): Result<BeginProjectsSyncResponse> {
		return get(
			path = "/api/projects/$userId/begin_sync",
			parse = { it.body() },
		)
	}

	suspend fun probeProjectChanges(
		items: List<ProjectHashItem>,
	): Result<ProjectsSyncProbeResponse> {
		return post(
			path = "/api/projects/$userId/sync_probe",
			parse = { it.body() },
			builder = {
				contentType(ContentType.Application.Json)
				setBody(ProjectsSyncProbeRequest(items))
			},
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
			path = "/api/projects/$userId/create",
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectName", projectName)
			},
			parse = { it.body() },
		)
	}
}