package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.HasProjectResponse
import com.darkrockstudios.apps.hammer.base.http.LoadSceneResponse
import com.darkrockstudios.apps.hammer.base.http.SaveSceneResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

class ServerProjectApi(
    httpClient: HttpClient,
    globalSettingsRepository: GlobalSettingsRepository
) : Api(httpClient, globalSettingsRepository) {

    suspend fun hasProject(userId: Long, projectName: String): Result<HasProjectResponse> {
        return get(
            path = "/project/$userId/$projectName/has_project",
            parse = { it.body() }
        )
    }

	suspend fun uploadScene(scene: SceneItem, path: List<String>, content: String): Result<SaveSceneResponse> {
		val projectName = scene.projectDef.name
		return post(
			path = "/project/$userId/$projectName/upload_scene/${scene.id}",
			parse = { it.body() },
			builder = {
				setBody(
					FormDataContent(
						Parameters.build {
							append("sceneName", scene.name)
							append("sceneContent", content)
							append("sceneOrder", scene.order.toString())
							append("scenePath", path.joinToString("/"))
						}
					)
				)
			}
		)
	}

	suspend fun downloadScene(projectDef: ProjectDef, sceneId: Int): Result<LoadSceneResponse> {
		val projectName = projectDef.name
		return post(
			path = "/project/$userId/$projectName/download_scene/$sceneId",
			parse = { it.body() },
		)
	}
}
