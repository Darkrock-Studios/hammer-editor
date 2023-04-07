package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.GetProjectsResponse
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*

class ServerProjectsApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
) : Api(httpClient, globalSettingsRepository) {

	suspend fun getProjects(): Result<GetProjectsResponse> {
		return get(
			path = "/projects/$userId",
			parse = { it.body() },
		)
	}

	suspend fun deleteProject(projectName: String): Result<String> {
		return get(path = "/projects/$userId/$projectName/delete")
	}

	suspend fun createProject(projectName: String): Result<String> {
		return get(path = "/projects/$userId/$projectName/create")
	}
}