package com.darkrockstudios.apps.hammer.common.server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import korlibs.io.compression.compress
import korlibs.io.compression.deflate.GZIP
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class ServerProjectApi(
	httpClient: HttpClient,
	globalSettingsRepository: GlobalSettingsRepository,
	private val json: Json,
	private val strRes: StrRes,
) : Api(httpClient, globalSettingsRepository, strRes) {

	suspend fun beginProjectSync(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
		clientState: ClientEntityState?,
		lite: Boolean
	): Result<ProjectSynchronizationBegan> {
		val compressed = clientState?.let {
			val json = json.encodeToString(clientState)
			json.toByteArray().compress(GZIP)
		}

		return post(
			path = "/api/project/$userId/$projectName/begin_sync",
			parse = { it.body() }
		) {
			url {
				parameters.append("projectId", projectId.id)
				if (lite) {
					parameters.append("lite", lite.toString())
				}
			}
			if (compressed != null) {
				setBody(compressed)
			}
		}
	}

	suspend fun endProjectSync(
		userId: Long,
		projectName: String,
		projectId: ProjectId,
		syncId: String,
		lastId: Int?,
		syncEnd: Instant?,
	): Result<String> {
		return post(
			path = "/api/project/$userId/$projectName/end_sync",
			parse = { it.body() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				url {
					parameters.append("projectId", projectId.id)
				}
				setBody(
					FormDataContent(
						Parameters.build {
							if (syncEnd != null) append("lastSync", syncEnd.toString())
							if (lastId != null) append("lastId", lastId.toString())
						}
					)
				)
			}
		)
	}

	suspend fun uploadEntity(
		projectName: String,
		projectId: ProjectId,
		entity: ApiProjectEntity,
		originalHash: String?,
		syncId: String,
		force: Boolean = false
	): Result<SaveEntityResponse> {
		return post(
			path = "/api/project/$userId/$projectName/upload_entity/${entity.id}",
			parse = { it.body() },
			builder = {
				contentType(ContentType.Application.Json)
				headers {
					append(HEADER_SYNC_ID, syncId)
					append(HEADER_ENTITY_TYPE, entity.type.name)
					if (originalHash != null) {
						append(HEADER_ORIGINAL_HASH, originalHash)
					}
				}
				url {
					parameters.append("force", force.toString())
					parameters.append("projectId", projectId.id)
				}
				when (entity) {
					is ApiProjectEntity.SceneEntity -> setBody(entity)
					is ApiProjectEntity.NoteEntity -> setBody(entity)
					is ApiProjectEntity.TimelineEventEntity -> setBody(entity)
					is ApiProjectEntity.EncyclopediaEntryEntity -> setBody(entity)
					is ApiProjectEntity.SceneDraftEntity -> setBody(entity)
				}
			},
			failureHandler = { response ->
				if (response.status == HttpStatusCode.Conflict) {
					val jsonStr = response.bodyAsText()
					when (entity.type) {
						ApiProjectEntity.Type.SCENE -> EntityConflictException.SceneConflictException(
							json.decodeFromString<ApiProjectEntity.SceneEntity>(
								jsonStr
							)
						)

						ApiProjectEntity.Type.NOTE -> EntityConflictException.NoteConflictException(
							json.decodeFromString<ApiProjectEntity.NoteEntity>(
								jsonStr
							)
						)

						ApiProjectEntity.Type.TIMELINE_EVENT -> EntityConflictException.TimelineEventConflictException(
							json.decodeFromString<ApiProjectEntity.TimelineEventEntity>(
								jsonStr
							)
						)

						ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> EntityConflictException.EncyclopediaEntryConflictException(
							json.decodeFromString<ApiProjectEntity.EncyclopediaEntryEntity>(
								jsonStr
							)
						)

						ApiProjectEntity.Type.SCENE_DRAFT -> EntityConflictException.SceneDraftConflictException(
							json.decodeFromString<ApiProjectEntity.SceneDraftEntity>(
								jsonStr
							)
						)
					}
				} else {
					defaultFailureHandler(response, strRes)
				}
			}
		)
	}

	suspend fun downloadEntity(
		projectName: String,
		projectId: ProjectId,
		entityId: Int,
		localHash: String?,
		syncId: String
	): Result<LoadEntityResponse> {
		return get(
			path = "/api/project/$userId/$projectName/download_entity/$entityId",
			parse = { response ->
				if (response.status == HttpStatusCode.NotModified) {
					throw EntityNotModifiedException(entityId)
				}
				val type = response.headers[HEADER_ENTITY_TYPE]?.let { type ->
					ApiProjectEntity.Type.fromString(type)
				} ?: throw IllegalStateException("Missing entity-type header")

				return@get when (type) {
					ApiProjectEntity.Type.SCENE -> LoadEntityResponse(response.body<ApiProjectEntity.SceneEntity>())
					ApiProjectEntity.Type.NOTE -> LoadEntityResponse(response.body<ApiProjectEntity.NoteEntity>())
					ApiProjectEntity.Type.TIMELINE_EVENT -> LoadEntityResponse(response.body<ApiProjectEntity.TimelineEventEntity>())
					ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> LoadEntityResponse(response.body<ApiProjectEntity.EncyclopediaEntryEntity>())
					ApiProjectEntity.Type.SCENE_DRAFT -> LoadEntityResponse(response.body<ApiProjectEntity.SceneDraftEntity>())
				}
			},
			failureHandler = { response ->
				when (response.status) {
					HttpStatusCode.NotModified -> EntityNotModifiedException(entityId)
					HttpStatusCode.NotFound -> EntityNotFoundException(entityId)
					else -> defaultFailureHandler(response, strRes)
				}
			},
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
					if (localHash != null) {
						append(HEADER_ENTITY_HASH, localHash)
					}
				}
				url {
					parameters.append("projectId", projectId.id)
				}
			}
		)
	}

	suspend fun deleteId(
		projectName: String,
		projectId: ProjectId,
		id: Int,
		syncId: String
	): Result<DeleteIdsResponse> {
		return get(
			path = "/api/project/$userId/$projectName/delete_entity/$id",
			parse = { it.body() },
			builder = {
				headers {
					append(HEADER_SYNC_ID, syncId)
				}
				url {
					parameters.append("projectId", projectId.id)
				}
			}
		)
	}
}

class EntityNotModifiedException(val entityId: Int) : Exception()
class EntityNotFoundException(val entityId: Int) : Exception()