package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.project.synchronizers.SceneSynchronizer
import com.darkrockstudios.apps.hammer.utilities.RandomString
import kotlinx.datetime.Clock
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

	suspend fun beginProjectSync(userId: Long, projectDef: ProjectDefinition): Result<String> {
		val newSyncId = syncIdGenerator.nextString()
		synchronized(synchronizationSessions) {
			return if (synchronizationSessions.containsKey(userId)) {
				return Result.failure(IllegalStateException("User $userId already has a synchronization session"))
			} else {
				val session = ProjectSynchronizationSession(
					userId = userId,
					projectDef = projectDef,
					started = clock.now(),
					syncId = newSyncId
				)
				synchronizationSessions[userId] = session
				Result.success(newSyncId)
			}
		}
	}

	suspend fun endProjectSync(userId: Long, projectDef: ProjectDefinition, syncId: String): Result<Boolean> {
		synchronized(synchronizationSessions) {
			val session = synchronizationSessions[userId]
			return if (session == null) {
				Result.failure(IllegalStateException("User $userId does not have a synchronization session"))
			} else {
				if (session.syncId != syncId) {
					Result.failure(IllegalStateException("Invalid sync id"))
				} else {
					synchronizationSessions.remove(userId)
					Result.success(true)
				}
			}
		}
	}

	fun hasProject(userId: Long, projectDef: ProjectDefinition, syncId: String): Boolean {
		if (validateSyncId(userId, syncId).not()) return false

		val userDir = getUserDirectory(userId)
		val projectDir = userDir / projectDef.name

		return fileSystem.exists(projectDir)
	}

	fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: ProjectEntity,
		syncId: String
	): Result<Boolean> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		ensureEntityDir(userId, projectDef)

		val result = when (entity) {
			is ProjectEntity.SceneEntity -> sceneSynchronizer.saveScene(userId, projectDef, entity)
		}
		return result
	}

	private fun ensureEntityDir(userId: Long, projectDef: ProjectDefinition) {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(entityDir)
	}

	fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		type: ProjectEntity.Type,
		syncId: String
	): Result<ProjectEntity> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		return when (type) {
			ProjectEntity.Type.SCENE -> sceneSynchronizer.loadScene(userId, projectDef, entityId)
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

class InvalidSyncIdException : Exception("Invalid sync id")