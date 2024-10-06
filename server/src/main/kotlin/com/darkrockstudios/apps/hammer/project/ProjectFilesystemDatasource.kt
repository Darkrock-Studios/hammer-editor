package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource.Companion.getUserDirectory
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import java.io.IOException

class ProjectFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
) : ProjectDatasource {

	override suspend fun checkProjectExists(
		userId: Long,
		projectDef: ProjectDefinition,
	): Boolean {
		val projectDir = getProjectDirectory(userId, projectDef)
		return fileSystem.exists(projectDir)
	}

	override suspend fun checkProjectExists(userId: Long, projectId: ProjectId): Boolean {
		TODO("Not yet implemented")
		return false
	}

	override suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition? {
		error("Not implemented for FileSystem datasource")
	}

	override suspend fun createProject(userId: Long, projectName: String): ProjectDefinition {
		val projectDef = ProjectDefinition(name = projectName, uuid = ProjectId.randomUUID())

		val projectDir = getProjectDirectory(userId, projectDef)
		fileSystem.createDirectories(projectDir)

		getProjectSyncDataPath(userId, projectDef).let { syncDataPath ->
			if (fileSystem.exists(syncDataPath).not()) {
				val data = ProjectSyncData(
					lastSync = Instant.DISTANT_PAST,
					lastId = 0,
					deletedIds = emptySet()
				)
				val dataJson = json.encodeToString(data)

				fileSystem.write(syncDataPath) {
					writeUtf8(dataJson)
				}
			}
		}

		ensureEntityDir(userId, projectDef)

		return projectDef
	}

	override suspend fun deleteProject(userId: Long, projectId: ProjectId): SResult<Unit> {
		val projectDef = ProjectDefinition("", uuid = projectId)
		val projectDir = getProjectDirectory(userId, projectDef, fileSystem)
		fileSystem.deleteRecursively(projectDir)

		return SResult.success(Unit)
	}

	override suspend fun loadProjectSyncData(
		userId: Long,
		projectDef: ProjectDefinition
	): ProjectSyncData {
		val file = getProjectSyncDataPath(userId, projectDef)

		return if (fileSystem.exists(file).not()) {
			val newData = ProjectSyncData(
				lastId = -1,
				lastSync = Instant.DISTANT_PAST,
				deletedIds = emptySet(),
			)

			fileSystem.write(file) {
				val syncDataJson = json.encodeToString(newData)
				writeUtf8(syncDataJson)
			}

			newData
		} else {
			val dataJson = fileSystem.read(file) {
				readUtf8()
			}
			json.decodeFromString(dataJson)
		}
	}

	override suspend fun updateSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	) {
		val data = loadProjectSyncData(userId, projectDef)
		val updated = action(data)
		val newSyncDataJson = json.encodeToString(updated)
		val path = getProjectSyncDataPath(userId, projectDef)
		fileSystem.write(path) {
			writeUtf8(newSyncDataJson)
		}
	}

	override suspend fun findLastId(
		userId: Long,
		projectDef: ProjectDefinition
	): Int? {
		val dir = getEntityDirectory(userId, projectDef)
		return fileSystem.list(dir)
			.mapNotNull { path -> parseEntityFilename(path) }
			.maxByOrNull { def -> def.id }?.id
	}

	override suspend fun findEntityType(
		entityId: Int,
		userId: Long,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type? {
		val dir = getEntityDirectory(userId, projectDef)
		fileSystem.list(dir).forEach { path ->
			val def = parseEntityFilename(path)
			if (def?.id == entityId) {
				return def.type
			}
		}
		return null
	}

	override suspend fun getEntityDefs(
		userId: Long,
		projectDef: ProjectDefinition,
		filter: (EntityDefinition) -> Boolean
	): List<EntityDefinition> {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		val entities = fileSystem.list(entityDir).mapNotNull {
			parseEntityFilename(it)
		}.filter(filter)
		return entities
	}

	override suspend fun getEntityDefsByType(
		userId: Long,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type
	): List<EntityDefinition> {
		TODO("Not yet implemented")
	}

	override suspend fun renameProject(
		userId: Long,
		projectId: ProjectId,
		newProjectName: String
	): Boolean {
		TODO("Not yet implemented")
		return false
	}

	override suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition? {
		TODO("Not yet implemented")
		return null
	}

	override suspend fun <T : ApiProjectEntity> storeEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<Unit> {
		return try {
			val path = getPath(
				userId = userId,
				projectDef = projectDef,
				entityId = entity.id,
				entityType = entityType,
			)
			val jsonString: String = json.encodeToString(serializer, entity)
			fileSystem.write(path) {
				writeUtf8(jsonString)
			}
			SResult.success(Unit)
		} catch (e: SerializationException) {
			SResult.failure(e)
		} catch (e: IllegalArgumentException) {
			SResult.failure(e)
		} catch (e: IOException) {
			SResult.failure(e)
		}
	}

	override suspend fun <T : ApiProjectEntity> loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<T> {
		val path = getPath(userId, entityType, projectDef, entityId)

		return try {
			val jsonString = fileSystem.read(path) {
				readUtf8()
			}

			val scene = json.decodeFromString(serializer, jsonString)
			SResult.success(scene)
		} catch (e: SerializationException) {
			SResult.failure(e)
		} catch (e: IllegalArgumentException) {
			SResult.failure(e)
		} catch (e: FileNotFoundException) {
			SResult.failure(EntityNotFound(entityId))
		} catch (e: IOException) {
			SResult.failure(e)
		}
	}

	override suspend fun loadEntityHash(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<String> {
		TODO("Not yet implemented")
	}

	override suspend fun deleteEntity(
		userId: Long,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit> {
		val path = getPath(userId, entityType, projectDef, entityId)
		return try {
			fileSystem.delete(path, false)
			SResult.success()
		} catch (e: IOException) {
			SResult.failure(e)
		}
	}

	private fun ensureEntityDir(userId: Long, projectDef: ProjectDefinition) {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(entityDir)
	}

	private fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition): Path {
		ensureEntityDir(userId, projectDef)
		return getEntityDirectory(userId, projectDef, fileSystem)
	}

	private fun getProjectDirectory(userId: Long, projectDef: ProjectDefinition): Path =
		getProjectDirectory(userId, projectDef, fileSystem)

	private fun getProjectSyncDataPath(userId: Long, projectDef: ProjectDefinition): Path {
		val dir = getProjectDirectory(userId, projectDef)
		return dir / SYNC_DATA_FILE
	}

	private fun getPath(
		userId: Long,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): Path = getEntityPath(userId, entityType, projectDef, entityId, fileSystem)

	companion object {
		private val ENTITY_FILENAME_REGEX = Regex("^([0-9]+)-([a-zA-Z_]+).json$")
		private const val SYNC_DATA_FILE = "syncData.json"
		private const val ENTITY_DIR = "entities"

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

		fun getProjectDirectory(
			userId: Long,
			projectDef: ProjectDefinition,
			fileSystem: FileSystem
		): Path {
			val dir = getUserDirectory(userId, fileSystem)
			return dir / projectDef.name
		}

		fun getEntityDirectory(
			userId: Long,
			projectDef: ProjectDefinition,
			fileSystem: FileSystem
		): Path {
			val projDir = getProjectDirectory(userId, projectDef, fileSystem)
			val dir = projDir / ENTITY_DIR

			if (fileSystem.exists(dir).not()) {
				fileSystem.createDirectories(dir)
			}

			return dir
		}

		fun getProjectSyncDataPath(
			userId: Long,
			projectDef: ProjectDefinition,
			fileSystem: FileSystem
		): Path {
			val dir = getProjectDirectory(userId, projectDef, fileSystem)
			return dir / SYNC_DATA_FILE
		}

		private fun getPathStub(entityType: ApiProjectEntity.Type): String =
			entityType.name.lowercase()

		fun getEntityPath(
			userId: Long,
			entityType: ApiProjectEntity.Type,
			projectDef: ProjectDefinition,
			entityId: Int,
			fileSystem: FileSystem,
		): Path {
			val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
			val filename = "$entityId-${getPathStub(entityType)}.json"
			return entityDir / filename
		}
	}
}