package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.synchronizers.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository.Companion.getUserDirectory
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent

class ProjectRepository(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val clock: Clock
) : KoinComponent {
	private val sceneSynchronizer: ServerSceneSynchronizer by inject()
	private val noteSynchronizer: ServerNoteSynchronizer by inject()
	private val timelineEventSynchronizer: ServerTimelineSynchronizer by inject()
	private val encyclopediaSynchronizer: ServerEncyclopediaSynchronizer by inject()
	private val sceneDraftSynchronizer: ServerSceneDraftSynchronizer by inject()

	private val projectsSessions: SyncSessionManager<ProjectsSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	private val sessionManager: SyncSessionManager<ProjectSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECT_SYNC_MANAGER)
	)

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

	suspend fun beginProjectSync(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean
	): Result<ProjectSynchronizationBegan> {

		val projectDir = getProjectDirectory(userId, projectDef)

		return if (projectsSessions.hasActiveSyncSession(userId) || sessionManager.hasActiveSyncSession(userId)) {
			Result.failure(IllegalStateException("User $userId already has a synchronization session"))
		} else {
			if (!fileSystem.exists(projectDir)) {
				createProject(userId, projectDef)
			}

			var projectSyncData = getProjectSyncData(userId, projectDef)

			if (projectSyncData.lastId < 0) {
				val lastId = findLastId(userId, projectDef)
				projectSyncData = projectSyncData.copy(lastId = lastId ?: -1)
			}

			val newSyncId = sessionManager.createNewSession(userId) { user: Long, sync: String ->
				ProjectSynchronizationSession(
					userId = user,
					projectDef = projectDef,
					started = clock.now(),
					syncId = sync
				)
			}

			val updateSequence = getUpdateSequence(userId, projectDef, clientState, lite)
			val syncBegan = ProjectSynchronizationBegan(
				syncId = newSyncId,
				lastId = projectSyncData.lastId,
				lastSync = projectSyncData.lastSync,
				idSequence = updateSequence,
				deletedIds = projectSyncData.deletedIds,
			)
			Result.success(syncBegan)
		}
	}

	suspend fun endProjectSync(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String,
		lastSync: Instant?,
		lastId: Int?,
	): Result<Boolean> {
		val session = sessionManager.findSession(userId)
		return if (session == null) {
			Result.failure(IllegalStateException("User $userId does not have a synchronization session"))
		} else {
			if (session.syncId != syncId) {
				Result.failure(IllegalStateException("Invalid sync id"))
			} else {
				// Update sync data if it was sent
				if (lastSync != null && lastId != null) {
					updateSyncData(userId, projectDef) {
						it.copy(
							lastSync = lastSync,
							lastId = lastId,
						)
					}
				}

				sessionManager.terminateSession(userId)
				Result.success(true)
			}
		}
	}

	suspend fun getProjectSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		syncId: String
	): Result<ProjectServerState> {
		if (validateSyncId(userId, syncId).not())
			return Result.failure(IllegalStateException("Sync Id not valid"))

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

	suspend fun saveEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: ApiProjectEntity,
		originalHash: String?,
		syncId: String,
		force: Boolean
	): Result<Boolean> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		ensureEntityDir(userId, projectDef)

		val result = when (entity) {
			is ApiProjectEntity.SceneEntity -> sceneSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.NoteEntity -> noteSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.TimelineEventEntity -> timelineEventSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.EncyclopediaEntryEntity -> encyclopediaSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)

			is ApiProjectEntity.SceneDraftEntity -> sceneDraftSynchronizer.saveEntity(
				userId,
				projectDef,
				entity,
				originalHash,
				force
			)
		}
		return result
	}

	suspend fun deleteEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String,
	): Result<Boolean> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		updateSyncData(userId, projectDef) {
			it.copy(
				deletedIds = it.deletedIds + entityId
			)
		}

		val entityType: ApiProjectEntity.Type =
			getEntityType(userId, projectDef, entityId) ?: return Result.failure(NoEntityTypeFound(entityId))

		when (entityType) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.deleteEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.NOTE -> noteSynchronizer.deleteEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.deleteEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.deleteEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.deleteEntity(userId, projectDef, entityId)
		}

		return Result.success(true)
	}

	private fun getEntityType(userId: Long, projectDef: ProjectDefinition, entityId: Int): ApiProjectEntity.Type? {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		val files = fileSystem.list(entityDir)
		for (entityPath in files) {
			ServerEntitySynchronizer.ENTITY_FILENAME_REGEX.matchEntire(entityPath.name)?.let { match ->
				val id = match.groupValues[1].toInt()
				if (id == entityId) {
					val typeStr = match.groupValues[2]
					ApiProjectEntity.Type.fromString(typeStr)?.let { type ->
						return type
					}
				}
			}
		}
		return null
	}

	private fun createProject(userId: Long, projectDef: ProjectDefinition) {
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
	}

	private fun ensureEntityDir(userId: Long, projectDef: ProjectDefinition) {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(entityDir)
	}

	suspend fun loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		syncId: String
	): Result<ApiProjectEntity> {
		if (validateSyncId(userId, syncId).not()) return Result.failure(InvalidSyncIdException())

		val type = findEntityType(entityId, userId, projectDef)
			?: return Result.failure(EntityNotFound(entityId))

		return when (type) {
			ApiProjectEntity.Type.SCENE -> sceneSynchronizer.loadEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.NOTE -> noteSynchronizer.loadEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.TIMELINE_EVENT -> timelineEventSynchronizer.loadEntity(userId, projectDef, entityId)
			ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> encyclopediaSynchronizer.loadEntity(
				userId,
				projectDef,
				entityId
			)

			ApiProjectEntity.Type.SCENE_DRAFT -> sceneDraftSynchronizer.loadEntity(userId, projectDef, entityId)
		}
	}

	private suspend fun findEntityType(
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

	private suspend fun findLastId(
		userId: Long,
		projectDef: ProjectDefinition
	): Int? {
		val dir = getEntityDirectory(userId, projectDef)
		return fileSystem.list(dir)
			.mapNotNull { path -> ServerEntitySynchronizer.parseEntityFilename(path) }
			.maxByOrNull { def -> def.id }?.id
	}

	private fun updateSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	) {
		val data = getProjectSyncData(userId, projectDef)
		val updated = action(data)
		val newSyncDataJson = json.encodeToString(updated)
		val path = getProjectSyncDataPath(userId, projectDef)
		fileSystem.write(path) {
			writeUtf8(newSyncDataJson)
		}
	}

	private suspend fun validateSyncId(userId: Long, syncId: String): Boolean {
		return !projectsSessions.hasActiveSyncSession(userId) &&
				sessionManager.validateSyncId(userId, syncId, true)
	}

	private suspend fun getUpdateSequence(
		userId: Long,
		projectDef: ProjectDefinition,
		clientState: ClientEntityState?,
		lite: Boolean
	): List<Int> {
		val updateSequence = mutableSetOf<Int>()
		if (lite.not()) {
			updateSequence += sceneSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += sceneDraftSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += noteSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += timelineEventSynchronizer.getUpdateSequence(userId, projectDef, clientState)
			updateSequence += encyclopediaSynchronizer.getUpdateSequence(userId, projectDef, clientState)
		}

		return updateSequence.toList()
	}

	companion object {
		const val SYNC_DATA_FILE = "syncData.json"
		const val ENTITY_DIR = "entities"

		fun getProjectDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getUserDirectory(userId, fileSystem)
			return dir / projectDef.name
		}

		fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val projDir = getProjectDirectory(userId, projectDef, fileSystem)
			val dir = projDir / ENTITY_DIR

			if (fileSystem.exists(dir).not()) {
				fileSystem.createDirectories(dir)
			}

			return dir
		}

		fun getProjectSyncDataPath(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getProjectDirectory(userId, projectDef, fileSystem)
			return dir / SYNC_DATA_FILE
		}
	}
}

data class ProjectServerState(val lastSync: Instant, val lastId: Int)

class InvalidSyncIdException : Exception("Invalid sync id")
class NoEntityTypeFound(val id: Int) : Exception("Could not find Type for Entity ID: $id")
class EntityNotFound(val id: Int) : Exception("Entity $id not found on server")