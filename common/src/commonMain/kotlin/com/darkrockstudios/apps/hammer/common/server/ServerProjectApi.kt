package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ServerProjectApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
	private val json: Json
) : Api(httpClient, globalSettingsRepository) {

	suspend fun beginProjectSync(userId: Long, projectName: String): Result<String> {
		return get(
			path = "/project/$userId/$projectName/begin_sync",
			parse = { it.body() }
		)
	}

	suspend fun endProjectSync(userId: Long, projectName: String, syncId: String): Result<String> {
		return get(
			path = "/project/$userId/$projectName/end_sync",
			parse = { it.body() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}

	suspend fun getProjectLastSync(userId: Long, projectName: String, syncId: String): Result<HasProjectResponse> {
		return get(
			path = "/project/$userId/$projectName/last_sync",
			parse = { it.body() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}

	suspend fun uploadScene(
		scene: SceneItem,
		path: List<Int>,
		content: String,
		syncId: String,
		force: Boolean = false
	): Result<SaveSceneResponse> {
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
							append("sceneType", scene.type.name)
							append("sceneOrder", scene.order.toString())
							append("scenePath", path.joinToString("/"))
							append("force", force.toString())
						}
					)
				)
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				etag()
			},
			failureHandler = { response ->
				val jsonStr = response.bodyAsText()
				val entity = json.decodeFromString<ApiProjectEntity.SceneEntity>(jsonStr)
				EntityConflictException.SceneConflictException(entity)
			}
		)
	}

	suspend fun downloadEntity(
		projectDef: ProjectDef,
		entityId: Int,
		localHash: String?,
		syncId: String
	): Result<LoadEntityResponse> {
		val projectName = projectDef.name
		return get(
			path = "/project/$userId/$projectName/download_entity/$entityId",
			parse = { response ->
				if (response.status == HttpStatusCode.NotModified) {
					throw EntityNotModifiedException(entityId)
				}
				val type = response.headers[HEADER_ENTITY_TYPE]?.let { type ->
					ApiProjectEntity.Type.fromString(type)
				} ?: throw IllegalStateException("Missing entity-type header")

				return@get when (type) {
					ApiProjectEntity.Type.SCENE -> {
						val entity = response.body<ApiProjectEntity.SceneEntity>()
						LoadEntityResponse(entity)
					}
				}
			},
			failureHandler = { response ->
				if (response.status == HttpStatusCode.NotModified) {
					EntityNotModifiedException(entityId)
				} else {
					defaultFailureHandler(response)
				}
			},
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
					if (localHash != null) {
						append(HEADER_ENTITY_HASH, localHash)
					}
				}
			}
		)
	}

	suspend fun setSyncData(projectDef: ProjectDef, syncId: String, lastId: Int, syncEnd: Instant): Result<String> {
		val projectName = projectDef.name
		return post(
			path = "/project/$userId/$projectName/set_sync_data",
			parse = { it.body() },
			builder = {
				setBody(
					FormDataContent(
						Parameters.build {
							append("lastSync", syncEnd.toEpochMilliseconds().toString())
							append("lastId", lastId.toString())
						}
					)
				)
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
			}
		)
	}
}

class EntityNotModifiedException(val entityId: Int) : Exception()