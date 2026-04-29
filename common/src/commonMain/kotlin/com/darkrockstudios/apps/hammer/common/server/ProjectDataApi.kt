package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataUploadRequest
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

class ProjectDataApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
	private val json: Json,
	private val strRes: StrRes,
) : Api(httpClient, globalSettingsRepository, strRes) {

	suspend fun getProjectData(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
	): Result<ProjectDataDto?> = get(
		path = "/api/project/$userId/$projectName/project_data",
		parse = { response ->
			if (response.status == HttpStatusCode.NoContent) null
			else response.body<ProjectDataDto>()
		},
	) {
		url {
			parameters.append("projectId", projectId.id)
		}
	}

	/** Throws [ProjectDataConflictException] (wrapped in `Result.failure`) when the server returns 409. */
	suspend fun uploadProjectData(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
		data: ProjectData,
		originalHash: String?,
	): Result<ProjectDataDto> = post(
		path = "/api/project/$userId/$projectName/project_data",
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
		url {
			parameters.append("projectId", projectId.id)
		}
		setBody(ProjectDataUploadRequest(data = data, originalHash = originalHash))
	}
}
