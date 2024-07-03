package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.readJsonOrNull
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectFilesystemDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.Path
import java.io.IOException
import kotlin.reflect.KClass

abstract class ServerEntitySynchronizer<T : ApiProjectEntity>(
	protected val fileSystem: FileSystem,
	protected val json: Json
) {
	abstract val entityType: ApiProjectEntity.Type
	abstract fun hashEntity(entity: T): String
	protected abstract val entityClazz: KClass<T>
	protected abstract val pathStub: String

	private fun hashEntity(userId: Long, projectDef: ProjectDefinition, entityId: Int): String? {
		val path = getPath(userId = userId, projectDef = projectDef, entityId = entityId)

		return if (fileSystem.exists(path)) {
			val existingEntity = fileSystem.readJsonOrNull(path, json, entityClazz)
			if (existingEntity != null) {
				hashEntity(existingEntity)
			} else {
				null
			}
		} else {
			null
		}
	}

	protected fun checkForConflict(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		originalHash: String?,
		force: Boolean,
	): EntityConflictException? {
		val path = getPath(userId = userId, projectDef = projectDef, entityId = entity.id)

		return if (!force) {
			if (fileSystem.exists(path)) {
				val existingEntity = fileSystem.readJsonOrNull(path, json, entityClazz)
				if (existingEntity != null) {
					val existingHash = hashEntity(existingEntity)

					if (originalHash != null && existingHash != originalHash) {
						EntityConflictException.fromEntity(existingEntity)
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
	}

	@OptIn(InternalSerializationApi::class)
	fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		originalHash: String?,
		force: Boolean,
	): SResult<Boolean> {
		val conflict = checkForConflict(
			userId,
			projectDef,
			entity,
			originalHash,
			force,
		)
		return if (conflict == null) {
			try {
				val path = getPath(userId = userId, projectDef = projectDef, entityId = entity.id)
				val jsonString: String = json.encodeToString(entityClazz.serializer(), entity)
				fileSystem.write(path) {
					writeUtf8(jsonString)
				}
				SResult.success(true)
			} catch (e: SerializationException) {
				SResult.failure(e)
			} catch (e: IllegalArgumentException) {
				SResult.failure(e)
			} catch (e: IOException) {
				SResult.failure(e)
			}
		} else {
			SResult.failure(conflict)
		}
	}

	@OptIn(InternalSerializationApi::class)
	fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
	): SResult<T> {
		val path = getPath(userId, projectDef, entityId)

		return try {
			val jsonString = fileSystem.read(path) {
				readUtf8()
			}

			val scene = json.decodeFromString(entityClazz.serializer(), jsonString)
			SResult.success(scene)
		} catch (e: SerializationException) {
			SResult.failure(e)
		} catch (e: IllegalArgumentException) {
			SResult.failure(e)
		} catch (e: IOException) {
			SResult.failure(e)
		}
	}

	private fun getPath(userId: Long, projectDef: ProjectDefinition, entityId: Int): Path {
		val entityDir =
			ProjectFilesystemDatasource.getEntityDirectory(userId, projectDef, fileSystem)
		val filename = "$entityId-$pathStub.json"
		return entityDir / filename
	}

	fun deleteEntity(userId: Long, projectDef: ProjectDefinition, entityId: Int) {
		val path = getPath(userId, projectDef, entityId)
		fileSystem.delete(path, false)
	}

	protected fun getEntityDefs(userId: Long, projectDef: ProjectDefinition): List<EntityDefinition> {
		val entityDir =
			ProjectFilesystemDatasource.getEntityDirectory(userId, projectDef, fileSystem)
		val entities = fileSystem.list(entityDir).mapNotNull {
			parseEntityFilename(it)
		}.filter { it.type == entityType }
		return entities
	}

	open fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?
	): List<Int> {
		val entities = getEntityDefs(userId, projectDef)
			.filter { def ->
				val clientEntityState = clientState?.entities?.find { it.id == def.id }
				if (clientEntityState != null) {
					val serverHash = hashEntity(userId, projectDef, def.id)
					clientEntityState.hash != serverHash
				} else {
					true
				}
			}

		return entities.map { it.id }
	}

	companion object {
		val ENTITY_FILENAME_REGEX = Regex("^([0-9]+)-([a-zA-Z_]+).json$")

		fun parseEntityFilename(path: Path): EntityDefinition? {
			val filename = path.name
			val match = ENTITY_FILENAME_REGEX.matchEntire(filename)
			return if (match != null) {
				val (id, typeStr) = match.destructured
				val type = ApiProjectEntity.Type.fromString(typeStr)
				if (type != null) {
					EntityDefinition(id.toInt(), type)
				} else {
					null
				}
			} else {
				null
			}
		}
	}
}

data class EntityDefinition(
	val id: Int,
	val type: ApiProjectEntity.Type,
)