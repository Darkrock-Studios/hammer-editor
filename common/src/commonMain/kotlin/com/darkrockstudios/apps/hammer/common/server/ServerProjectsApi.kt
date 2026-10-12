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
import io.ktor.client.statement.*
import io.ktor.http.*

class ServerProjectsApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun beginProjectsSync(): Result<BeginProjectsSyncResponse> {
		return postWithLegacyGetFallback(
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
		return postWithLegacyGetFallback(
			path = "/api/projects/$userId/end_sync",
			parse = { it.bodyAsText() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}

	suspend fun deleteProject(projectId: ProjectId, syncId: String): Result<String> {
		return postWithLegacyGetFallback(
			path = "/api/projects/$userId/delete",
			parse = { it.bodyAsText() },
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
		return postWithLegacyGetFallback(
			path = "/api/projects/$userId/rename",
			parse = { it.bodyAsText() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", projectId.id)
				parameter("projectName", newName)
			}
		)
	}

	/**
	 * Idempotent on [projectId]. The response names a different id when the account already holds
	 * a project called [projectName]; a 409 carries `ApiErrorCode.PROJECT_DELETED` or
	 * `ApiErrorCode.PROJECT_ID_TAKEN`.
	 */
	suspend fun createProject(
		projectName: String,
		projectId: ProjectId,
		syncId: String,
	): Result<CreateProjectResponse> {
		return post(
			path = "/api/projects/$userId/create",
			parse = { it.body() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectName", projectName)
				parameter("projectId", projectId.id)
			},
		)
	}
}
