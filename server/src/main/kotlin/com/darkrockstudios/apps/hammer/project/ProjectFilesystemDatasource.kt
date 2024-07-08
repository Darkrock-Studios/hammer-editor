package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEntitySynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource.Companion.getUserDirectory
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class ProjectFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
) : ProjectDatasource {

	override fun checkProjectExists(
		userId: Long,
		projectDef: ProjectDefinition,
	): Boolean {
		val projectDir = getProjectDirectory(userId, projectDef)
		return fileSystem.exists(projectDir)
	}

	override fun createProject(userId: Long, projectDef: ProjectDefinition) {
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
	}

	override fun deleteProject(userId: Long, projectName: String): Result<Unit> {
		val projectDef = ProjectDefinition(projectName)
		val projectDir = getProjectDirectory(userId, projectDef, fileSystem)
		fileSystem.deleteRecursively(projectDir)

		return Result.success(Unit)
	}

	override fun loadProjectSyncData(userId: Long, projectDef: ProjectDefinition): ProjectSyncData {
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

	override fun updateSyncData(
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
			.mapNotNull { path -> ServerEntitySynchronizer.parseEntityFilename(path) }
			.maxByOrNull { def -> def.id }?.id
	}

	override suspend fun findEntityType(
		entityId: Int,
		userId: Long,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type? {
		val dir = getEntityDirectory(userId, projectDef)
		fileSystem.list(dir).forEach { path ->
			val def = ServerEntitySynchronizer.parseEntityFilename(path)
			if (def?.id == entityId) {
				return def.type
			}
		}
		return null
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

	companion object {
		const val SYNC_DATA_FILE = "syncData.json"
		const val ENTITY_DIR = "entities"

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
	}
}