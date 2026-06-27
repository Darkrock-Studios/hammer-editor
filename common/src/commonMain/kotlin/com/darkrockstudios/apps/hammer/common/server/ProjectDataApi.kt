package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataUploadRequest
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class ProjectDataApi(
	httpClient: HttpClient,
	globalSettingsStore: GlobalSettingsStore,
	private val json: Json,
	private val strRes: StrRes,
) : Api(httpClient, globalSettingsStore, strRes) {

	suspend fun getProjectData(
		userId: Long,
		projectId: ProjectId,
	): Result<ProjectDataDto?> = get(
		path = "/api/project/$userId/${projectId.id}/project_data",
		parse = { response ->
			if (response.status == HttpStatusCode.NoContent) null
			else response.body<ProjectDataDto>()
		},
	)

	/** Throws [ProjectDataConflictException] (wrapped in `Result.failure`) when the server returns 409. */
	suspend fun uploadProjectData(
		userId: Long,
		projectId: ProjectId,
		data: ProjectData,
		originalHash: String?,
	): Result<ProjectDataDto> = post(
		path = "/api/project/$userId/${projectId.id}/project_data",
		parse = { it.body<ProjectDataDto>() },
		failureHandler = { response ->
			if (response.status == HttpStatusCode.Conflict) {
				val body = json.decodeFromString<ProjectDataConflictDto>(response.bodyAsText())
				ProjectDataConflictException(body)
			} else {
				defaultFailureHandler(response, strRes)
			}
		},
	) {
		contentType(ContentType.Application.Json)
		setBody(ProjectDataUploadRequest(data = data, originalHash = originalHash))
	}
}
