package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.SceneHash
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.readJsonOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.io.IOException

class SceneSynchronizer(
	private val fileSystem: FileSystem,
	private val json: Json
) : EntitySynchronizer {
	fun saveScene(
		userId: Long,
		projectDef: ProjectDefinition,
		sceneEntity: ApiProjectEntity.SceneEntity,
		force: Boolean
	): Result<Boolean> {
		val path = getPath(userId = userId, projectDef = projectDef, entityId = sceneEntity.id)

		val conflict = if (!force) {
			val incomingHash = SceneHash.hashScene(
				id = sceneEntity.id,
				order = sceneEntity.order,
				title = sceneEntity.name,
				type = sceneEntity.sceneType,
			)

			if (fileSystem.exists(path)) {
				val existingScene = fileSystem.readJsonOrNull<ApiProjectEntity.SceneEntity>(path, json)
				if (existingScene != null) {
					val existingHash = SceneHash.hashScene(
						id = existingScene.id,
						order = existingScene.order,
						title = existingScene.name,
						type = existingScene.sceneType,
					)

					if (existingHash != incomingHash) {
						EntityConflictException.SceneConflictException(existingScene)
					} else {
						null
					}
				} else {
					null
				}
			} else {
				null
			}
		} else {
			null
		}

		return if (conflict == null) {
			try {
				val jsonString: String = json.encodeToString(sceneEntity)
				fileSystem.write(path) {
					writeUtf8(jsonString)
				}
				Result.success(true)
			} catch (e: SerializationException) {
				Result.failure(e)
			} catch (e: IllegalArgumentException) {
				Result.failure(e)
			} catch (e: IOException) {
				Result.failure(e)
			}
		} else {
			Result.failure(conflict)
		}
	}

	private fun getPath(userId: Long, projectDef: ProjectDefinition, entityId: Int): Path {
		val entityDir = ProjectRepository.getEntityDirectory(userId, projectDef, fileSystem)
		val filename = "$entityId-$TYPE_STUB.json"
		return entityDir / filename
	}

	fun loadScene(userId: Long, projectDef: ProjectDefinition, entityId: Int): Result<ApiProjectEntity.SceneEntity> {
		val path = getPath(userId, projectDef, entityId)

		return try {
			val jsonString = fileSystem.read(path) {
				readUtf8()
			}

			val scene = json.decodeFromString(ApiProjectEntity.SceneEntity.serializer(), jsonString)
			Result.success(scene)
		} catch (e: SerializationException) {
			Result.failure(e)
		} catch (e: IllegalArgumentException) {
			Result.failure(e)
		} catch (e: IOException) {
			Result.failure(e)
		}
	}

	companion object {
		const val TYPE_STUB = "scene"
	}
}