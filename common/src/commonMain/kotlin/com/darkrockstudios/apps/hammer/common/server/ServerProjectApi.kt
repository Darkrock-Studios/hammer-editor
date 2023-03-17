package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*

class ServerProjectApi(
    httpClient: HttpClient,
    globalSettingsRepository: GlobalSettingsRepository
) : Api(httpClient, globalSettingsRepository) {
    suspend fun hasProject(userId: Long, projectName: String): Result<HasProjectResponse> {
        return get(
            path = "/project/$userId/$projectName/has_project/$projectName",
            parse = { it.body() }
        )
    }
}

