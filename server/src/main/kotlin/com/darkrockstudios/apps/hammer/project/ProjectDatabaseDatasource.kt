package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.DeletedEntityDao
import com.darkrockstudios.apps.hammer.database.DeletedProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.StoryEntityDao
import com.darkrockstudios.apps.hammer.database.parseLastSync
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ProjectDatabaseDatasource(
	private val projectDao: ProjectDao,
	private val accountDao: AccountDao,
	private val deletedProjectDao: DeletedProjectDao,
	private val storyEntityDao: StoryEntityDao,
	private val deletedEntityDao: DeletedEntityDao,
	private val encryptor: ContentEncryptor,
	private val json: Json,
) : ProjectDatasource {

	override suspend fun loadProjectSyncData(
		userId: Long,
		projectDef: ProjectDefinition
	): ProjectSyncData {
		val project = projectDao.getProjectData(userId, projectDef.uuid)
			?: error("Project not found $userId : $projectDef")
		val deletedIds = deletedEntityDao.getDeletedEntities(userId, project.id)
			.map { it.entity_id.toInt() }
			.toSet()

		return ProjectSyncData(
			lastSync = project.parseLastSync(),
			lastId = project.last_id.toInt(),
			deletedIds = deletedIds
		)
	}

	override suspend fun createProject(userId: Long, projectName: String): ProjectDefinition {
		val existingProject = projectDao.findProjectData(userId = userId, projectName = projectName)
		val uuid = if (existingProject == null) {
			val newUuid = ProjectId.randomUUID()
			projectDao.createProject(
				userId = userId,
				uuid = newUuid,
				projectName = projectName,
			)
			newUuid
		} else {
			ProjectId(existingProject.uuid)
		}
		return ProjectDefinition(
			name = projectName,
			uuid = uuid,
		)
	}

	override suspend fun deleteProject(userId: Long, projectId: ProjectId): SResult<Unit> {
		projectDao.deleteProject(
			userId = userId,
			projectId = projectId,
		)

		deletedProjectDao.recordProjectDeleted(
			userId = userId,
			projectId = projectId,
		)

		return SResult.success(Unit)
	}

	override suspend fun checkProjectExists(userId: Long, projectDef: ProjectDefinition): Boolean {
		return checkProjectExists(userId, projectDef.uuid)
	}

	override suspend fun checkProjectExists(userId: Long, projectId: ProjectId): Boolean {
		return projectDao.hasProject(userId, projectId)
	}

	override suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition? {
		val project = projectDao.findProjectData(userId, projectName)
		return if (project != null) {
			ProjectDefinition.wrap(
				name = project.name,
				uuid = project.uuid,
			)
		} else {
			null
		}
	}

	override suspend fun updateSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	) {
		val updated = action(loadProjectSyncData(userId, projectDef))

		projectDao.updateSyncData(
			lastId = updated.lastId.toLong(),
			lastSync = updated.lastSync,
			userId = userId,
			projectName = projectDef.name,
		)

		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		updated.deletedIds.forEach { entityId ->
			// If it exists, or isn't already deleted, delete it
			if (storyEntityDao.checkExists(
					userId = userId,
					projectId = projectId,
					id = entityId.toLong()
				) || deletedEntityDao.isDeleted(
					userId = userId,
					projectId = projectId,
					id = entityId.toLong(),
				).not()
			) {
				storyEntityDao.deleteEntity(
					userId = userId,
					projectId = projectId,
					id = entityId.toLong(),
				)
				deletedEntityDao.markEntityDeleted(
					userId = userId,
					projectId = projectId,
					id = entityId.toLong(),
				)
			}
		}
	}

	override suspend fun findLastId(userId: Long, projectDef: ProjectDefinition): Int? {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		return storyEntityDao.findMaxId(userId, projectId)?.toInt()
	}

	override suspend fun findEntityType(
		entityId: Int,
		userId: Long,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type? {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		val typeId = storyEntityDao.getType(userId, projectId, entityId.toLong())
		return ApiProjectEntity.Type.fromString(typeId)
	}

	override suspend fun <T : ApiProjectEntity> storeEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>
	): SResult<Unit> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		val hash = EntityHasher.hashEntity(entity)
		val jsonString: String = json.encodeToString(serializer, entity)

		val account = accountDao.getAccount(userId) ?: error("User not found $userId")
		val encrypted = encryptor.encrypt(jsonString, account.cipher_secret)

		val result = storyEntityDao.upsert(
			userId = userId,
			projectId = projectId,
			id = entity.id.toLong(),
			type = entityType.toStringId(),
			content = encrypted,
			cipher = encryptor.cipherName(),
			hash = hash,
		)
		return result
	}

	override suspend fun <T : ApiProjectEntity> loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>
	): SResult<T> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		val dbEntity = storyEntityDao.getEntity(
			userId = userId,
			projectId = projectId,
			id = entityId.toLong(),
		)

		return if (dbEntity == null) {
			SResult.failure(
				error = "Entity not found. userId=$userId projectId=$projectId entityId=$entityId",
				exception = EntityNotFound(entityId)
			)
		} else if (dbEntity.type.compareTo(entityType.toStringId(), ignoreCase = true) != 0) {
			SResult.failure(
				"Invalid entity type. userId=$userId projectId=$projectId entityId=$entityId entityType=$entityType",
				exception = EntityTypeConflictException(
					id = entityId,
					existingType = dbEntity.type,
					submittedType = entityType.toStringId()
				)
			)
		} else {
			try {
				val account = accountDao.getAccount(userId) ?: error("User not found $userId")
				val decrypted = encryptor.decrypt(dbEntity.content, account.cipher_secret)
				val entity = json.decodeFromString(serializer, decrypted)
				SResult.success(entity)
			} catch (e: SerializationException) {
				SResult.failure(e)
			}
		}
	}

	override suspend fun loadEntityHash(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<String> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		val hash = storyEntityDao.getEntityHash(userId, projectId, entityId.toLong())
		return if (hash != null) {
			SResult.success(hash)
		} else {
			SResult.failure(EntityNotFound(entityId))
		}
	}

	override suspend fun deleteEntity(
		userId: Long,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		storyEntityDao.deleteEntity(
			userId = userId,
			projectId = projectId,
			id = entityId.toLong(),
		)
		deletedEntityDao.markEntityDeleted(
			userId = userId,
			projectId = projectId,
			id = entityId.toLong(),
		)
		return SResult.success(Unit)
	}

	override suspend fun getEntityDefs(
		userId: Long,
		projectDef: ProjectDefinition,
		filter: (EntityDefinition) -> Boolean
	): List<EntityDefinition> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		return storyEntityDao.getEntityDefs(
			userId = userId,
			projectId = projectId,
		).filter { filter(it) }
	}

	override suspend fun getEntityDefsByType(
		userId: Long,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type,
	): List<EntityDefinition> {
		val projectId = projectDao.getProjectId(userId, projectDef.uuid)
		return storyEntityDao.getEntityDefs(
			userId = userId,
			projectId = projectId,
			type = type,
		)
	}

	override suspend fun renameProject(
		userId: Long,
		projectId: ProjectId,
		newProjectName: String
	): Boolean {
		projectDao.updateProjectName(
			userId = userId,
			projectUuid = projectId,
			newName = newProjectName,
		)

		return true
	}

	override suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition? {
		val projectData = projectDao.getProjectData(userId, projectId)
		return if (projectData != null) {
			ProjectDefinition.wrap(
				name = projectData.name,
				uuid = projectData.uuid,
			)
		} else {
			null
		}
	}
}