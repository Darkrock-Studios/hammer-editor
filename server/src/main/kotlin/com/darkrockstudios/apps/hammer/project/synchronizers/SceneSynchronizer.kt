package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntity
import com.darkrockstudios.apps.hammer.project.ProjectRepository
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
		sceneEntity: ProjectEntity.SceneEntity
	): Result<Boolean> {
		val path = getPath(userId, projectDef, sceneEntity)

		return try {
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
	}

	private fun getPath(userId: Long, projectDef: ProjectDefinition, sceneEntity: ProjectEntity.SceneEntity): Path {
		val entityDir = ProjectRepository.getEntityDirectory(userId, projectDef, fileSystem)
		val filename = "${sceneEntity.id}-$TYPE_STUB.json"
		return entityDir / filename
	}

	companion object {
		const val TYPE_STUB = "scene"
	}
}