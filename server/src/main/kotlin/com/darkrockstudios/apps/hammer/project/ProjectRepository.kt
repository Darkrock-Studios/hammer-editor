package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.project.synchronizers.SceneSynchronizer
import com.darkrockstudios.apps.hammer.utilities.RandomString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class ProjectRepository(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val sceneSynchronizer: SceneSynchronizer,
	private val clock: Clock
) {
	private val syncIdGenerator = RandomString(30)
	private val synchronizationSessions = mutableMapOf<Long, ProjectSynchronizationSession>()

	fun getRootDirectory(): Path = getRootDirectory(fileSystem)
	fun getUserDirectory(userId: Long): Path = getUserDirectory(userId, fileSystem)
	fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition): Path =
		getEntityDirectory(userId, projectDef, fileSystem)

	fun getProjectDirectory(userId: Long, projectDef: ProjectDefinition): Path =
		getProjectDirectory(userId, projectDef, fileSystem)

	private fun getProjectSyncDataPath(userId: Long, projectDef: ProjectDefinition): Path {
		val dir = getProjectDirectory(userId, projectDef)
		return dir / SYNC_DATA_FILE
	}

	private fun getProjectSyncData(userId: Long, projectDef: ProjectDefinition): ProjectSyncData {
		val file = getProjectSyncDataPath(userId, projectDef)

		return if (fileSystem.exists(file).not()) {
			val newData = ProjectSyncData(
				lastId = -1,
				lastSync = Instant.DISTANT_PAST,
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

	fun createUserData(userId: Long) {
		val userDir = getUserDirectory(userId)

		fileSystem.createDirectories(userDir)

		val dataFile = userDir / DATA_FILE
		val data = defaultData(userId)
		val dataJson = json.encodeToString(data)
		fileSystem.write(dataFile) {
			writeUtf8(dataJson)
		}

		/*
		val projectsFile = userDir / PROJECT_FILE
		// Create the zip file with a place holder entry
		//val zipFs = fileSystem.openZip(projectsFile)
		val zipFile = File(projectsFile.toString())
		val out = ZipOutputStream(FileOutputStream(zipFile))
		val e = ZipEntry(".")
		out.putNextEntry(e)
		val placeHolderData = "\n".toByteArray()
		out.write(placeHolderData, 0, placeHolderData.size)
		out.closeEntry()
		out.close()
		*/
	}

	fun userDataExists(userId: Long): Boolean {
		val userDir = getUserDirectory(userId)
		return fileSystem.exists(userDir)
	}

	private fun hasActiveSyncSession(userId: Long): Boolean {
		synchronized(synchronizationSessions) {
			val session = synchronizationSessions[userId]
			return if (session == null || session.isExpired(clock)) {
				synchronizationSessions.remove(userId)
				false
			} else {
				true
			}
		}
	}

	suspend fun beginProjectSync(userId: Long, projectDef: ProjectDefinition): Result<ProjectSynchronizationBegan> {
		val newSyncId = syncIdGenerator.nextString()
		val projectDir = getProjectDirectory(userId, projectDef)

		synchronized(synchronizationSessions) {
			return if (hasActiveSyncSession(userId)) {
				Result.failure(IllegalStateException("User $userId already has a synchronization session"))
			} else {
				if (!fileSystem.exists(projectDir)) {
					createProject(userId, projectDef)
				}

				val projectSyncData = getProjectSyncData(userId, projectDef)

				val session = ProjectSynchronizationSession(
					userId = userId,
					projectDef = projectDef,
					started = clock.now(),
					syncId = newSyncId
				)
				synchronizationSessions[userId] = session

				val syncBegan = ProjectSynchronizationBegan(
					syncId = newSyncId,
					lastId = projectSyncData.lastId,
					lastSync = projectSyncData.lastSync
				)
				Result.success(syncBegan)
			}
		}
	}

	suspend fun endProjectSync(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String,
		lastSyncMs: Long,
		lastId: Int
	): Result<Boolean> {
		synchronized(synchronizationSessions) {
			val session = synchronizationSessions[userId]
			return if (session == null) {
				Result.failure(IllegalStateException("User $userId does not have a synchronization session"))
			} else {
				if (session.syncId != syncId) {
					Result.failure(IllegalStateException("Invalid sync id"))
				} else {

					val lastSync = Instant.fromEpochSeconds(lastSyncMs)
					// Update sync data
					val synDataPath = getProjectSyncDataPath(userId, projectDef)
					val syncData = getProjectSyncData(userId, projectDef)
					val newSyncData = syncData.copy(lastSync = lastSync, lastId = lastId)
					val newSyncDataJson = json.encodeToString(newSyncData)
					fileSystem.write(synDataPath) {
						writeUtf8(newSyncDataJson)
					}

					synchronizationSessions.remove(userId)
					Result.success(true)
				}
			}
		}
	}

	fun getProjectLastSync(userId: Long, projectDef: ProjectDefinition, syncId: String): Result<ProjectServerState> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(IllegalStateException("Project does not exist"))

		val projectDir = getProjectDirectory(userId, projectDef)

		return if (fileSystem.exists(projectDir)) {
			val projectSyncData = getProjectSyncData(userId, projectDef)
			Result.success(
				ProjectServerState(
					lastSync = projectSyncData.lastSync,
					lastId = projectSyncData.lastId
				)
			)
		} else {
			Result.failure(IllegalStateException("Project does not exist"))
		}
	}

	fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: ApiProjectEntity,
		syncId: String,
		force: Boolean
	): Result<Boolean> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		ensureEntityDir(userId, projectDef)

		val result = when (entity) {
			is ApiProjectEntity.SceneEntity -> sceneSynchronizer.saveScene(userId, projectDef, entity, force)
		}
		return result
	}

	private fun createProject(userId: Long, projectDef: ProjectDefinition) {
		val projectDir = getProjectDirectory(userId, projectDef)
		fileSystem.createDirectories(projectDir)

		getProjectSyncDataPath(userId, projectDef).let { syncDataPath ->
			if (fileSystem.exists(syncDataPath).not()) {
				val data = ProjectSyncData(
					lastSync = Instant.DISTANT_PAST,
					lastId = 0
				)
				val dataJson = json.encodeToString(data)

				fileSystem.write(syncDataPath) {
					writeUtf8(dataJson)
				}
			}
		}
	}

	private fun ensureEntityDir(userId: Long, projectDef: ProjectDefinition) {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(entityDir)
	}

	fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		type: ApiProjectEntity.Type,
		syncId: String
	): Result<ApiProjectEntity> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		return when (type) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.loadScene(userId, projectDef, entityId)
		}
	}

	private fun validateSyncId(userId: Long, syncId: String): Boolean {
		synchronized(synchronizationSessions) {
			val session = synchronizationSessions[userId]
			return if (session?.syncId == syncId) {
				if (session.isExpired(clock).not()) {
					session.updateLastAccessed(clock)
					true
				} else {
					synchronizationSessions.remove(userId)
					false
				}
			} else {
				false
			}
		}
	}

	companion object {
		private const val DATA_DIRECTORY = "user_data"
		const val DATA_FILE = "data.json"
		const val SYNC_DATA_FILE = "syncData.json"

		const val ENTITY_DIR = "entities"

		fun defaultData(userId: Long): SyncData {
			return SyncData()
		}

		fun getRootDirectory(fileSystem: FileSystem): Path = getRootDataDirectory(fileSystem) / DATA_DIRECTORY

		fun getUserDirectory(userId: Long, fileSystem: FileSystem): Path {
			val dir = getRootDirectory(fileSystem)
			return dir / userId.toString()
		}

		fun getProjectDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getUserDirectory(userId, fileSystem)
			return dir / projectDef.name
		}

		fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getProjectDirectory(userId, projectDef, fileSystem)
			return dir / ENTITY_DIR
		}
	}
}

data class ProjectServerState(val lastSync: Instant, val lastId: Int)

class InvalidSyncIdException : Exception("Invalid sync id")