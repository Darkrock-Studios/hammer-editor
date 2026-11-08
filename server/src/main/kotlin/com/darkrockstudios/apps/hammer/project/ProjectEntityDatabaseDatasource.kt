package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.database.*
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.utilities.SResult
import java.security.GeneralSecurityException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ProjectEntityDatabaseDatasource(
	private val projectDao: ProjectDao,
	private val accountDao: AccountDao,
	private val deletedProjectDao: DeletedProjectDao,
	private val storyEntityDao: StoryEntityDao,
	private val deletedEntityDao: DeletedEntityDao,
	private val encryptor: ContentEncryptor,
	private val encryptorRegistry: ContentEncryptorRegistry,
	private val json: Json,
	private val maxContentLength: Int = MAX_ENTITY_CONTENT_LENGTH,
) : ProjectEntityDatasource {

	companion object {
		// Must match the story_entity_content_max CHECK in StoryEntity.sq. 64 MiB.
		const val MAX_ENTITY_CONTENT_LENGTH = 67_108_864

		private val logger = LoggerFactory.getLogger(ProjectEntityDatabaseDatasource::class.java)
	}

	override suspend fun loadProjectSyncData(
		owner: OwnerId,
		projectDef: ProjectDefinition
	): ProjectSyncData {
		val project = projectDao.getProjectData(owner, projectDef.uuid)
			?: error("Project not found ${owner.id} : $projectDef")
		val deletedIds = deletedEntityDao.getDeletedEntities(owner.id, project.id)
			.map { it.entity_id.toInt() }
			.toSet()

		return ProjectSyncData(
			lastSync = project.parseLastSync(),
			lastId = project.last_id.toInt(),
			deletedIds = deletedIds
		)
	}

	override suspend fun createProject(
		owner: OwnerId,
		projectName: String,
		projectId: ProjectId,
	): ProjectDefinition? {
		val inserted = projectDao.createProject(
			owner = owner,
			uuid = projectId,
			projectName = projectName,
		)
		return if (inserted) ProjectDefinition(name = projectName, uuid = projectId) else null
	}

	override suspend fun deleteProject(owner: OwnerId, projectId: ProjectId): SResult<Unit> {
		// Look up the numeric project id before deleting the project row so we
		// can scrub the dependent story_entity rows explicitly. We avoid relying
		// on `ON DELETE CASCADE` here so the behavior is the same in tests
		// (which disable FK enforcement) as it is in production.
		val internalId = projectDao.getProjectIdOrNull(owner, projectId)
		if (internalId != null) {
			storyEntityDao.deleteAllForProject(owner.id, internalId)
		}

		projectDao.deleteProject(
			owner = owner,
			projectId = projectId,
		)

		deletedProjectDao.recordProjectDeleted(
			userId = owner.id,
			projectId = projectId,
		)

		return SResult.success(Unit)
	}

	override suspend fun checkProjectExists(owner: OwnerId, projectDef: ProjectDefinition): Boolean {
		return checkProjectExists(owner, projectDef.uuid)
	}

	override suspend fun checkProjectExists(owner: OwnerId, projectId: ProjectId): Boolean {
		return projectDao.hasProject(owner, projectId)
	}

	override suspend fun findProjectByName(owner: OwnerId, projectName: String): ProjectDefinition? {
		val project = projectDao.findProjectData(owner.id, projectName)
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
		owner: OwnerId,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	) {
		val updated = action(loadProjectSyncData(owner, projectDef))

		projectDao.updateSyncData(
			lastId = updated.lastId.toLong(),
			lastSync = updated.lastSync,
			owner = owner,
			projectName = projectDef.name,
		)

		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		updated.deletedIds.forEach { entityId ->
			// If it exists, or isn't already deleted, delete it
			if (storyEntityDao.checkExists(
					userId = owner.id,
					projectId = projectId,
					id = entityId.toLong()
				) || deletedEntityDao.isDeleted(
					userId = owner.id,
					projectId = projectId,
					id = entityId.toLong(),
				).not()
			) {
				storyEntityDao.deleteEntity(
					userId = owner.id,
					projectId = projectId,
					id = entityId.toLong(),
				)
				deletedEntityDao.markEntityDeleted(
					userId = owner.id,
					projectId = projectId,
					id = entityId.toLong(),
				)
			}
		}
	}

	override suspend fun findLastId(owner: OwnerId, projectDef: ProjectDefinition): Int? {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		return storyEntityDao.findMaxId(owner.id, projectId)?.toInt()
	}

	override suspend fun findEntityType(
		entityId: Int,
		owner: OwnerId,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type? {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		val typeId = storyEntityDao.getType(owner.id, projectId, entityId.toLong())
		return ApiProjectEntity.Type.fromString(typeId)
	}

	override suspend fun <T : ApiProjectEntity> storeEntity(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entity: T,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>
	): SResult<Unit> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		val hash = entity.hash()
		val jsonString: String = json.encodeToString(serializer, entity)

		val account = accountDao.getAccount(owner.id) ?: error("User not found ${owner.id}")
		val encrypted = encryptor.encrypt(jsonString, account.cipher_secret)

		if (encrypted.length > maxContentLength) {
			return SResult.failure(
				EntityTooLargeException(entity.id, encrypted.length, maxContentLength)
			)
		}

		val result = storyEntityDao.upsert(
			userId = owner.id,
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
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>
	): SResult<T> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		val dbEntity = storyEntityDao.getEntity(
			userId = owner.id,
			projectId = projectId,
			id = entityId.toLong(),
		)

		return if (dbEntity == null) {
			SResult.failure(
				error = "Entity not found. owner=${owner.id} projectId=$projectId entityId=$entityId",
				exception = EntityNotFound(entityId)
			)
		} else if (dbEntity.type.compareTo(entityType.toStringId(), ignoreCase = true) != 0) {
			SResult.failure(
				"Invalid entity type. owner=${owner.id} projectId=$projectId entityId=$entityId entityType=$entityType",
				exception = EntityTypeConflictException(
					id = entityId,
					existingType = dbEntity.type,
					submittedType = entityType.toStringId()
				)
			)
		} else {
			try {
				val account = accountDao.getAccount(owner.id) ?: error("User not found ${owner.id}")
				val rowEncryptor = encryptorRegistry.resolve(dbEntity.cipher)
				val decrypted = rowEncryptor.decrypt(dbEntity.content, account.cipher_secret)
				val entity = json.decodeFromString(serializer, decrypted)
				repairStaleHash(owner, projectId, entityId, dbEntity.hash, entity)
				SResult.success(entity)
			} catch (e: SerializationException) {
				SResult.failure(e)
			} catch (e: GeneralSecurityException) {
				// Wrong key or corrupt ciphertext for the row's cipher tag.
				SResult.failure(e)
			} catch (e: IllegalArgumentException) {
				// Ciphertext that isn't valid Base64.
				SResult.failure(e)
			}
		}
	}

	/**
	 * The hash column is derived metadata; the stored content is the truth. A mismatch
	 * (e.g. the hash algorithm changed since the row was written) is repaired in place so
	 * downloads and update-sequence checks converge instead of flagging the entity forever.
	 */
	private suspend fun repairStaleHash(
		owner: OwnerId,
		projectId: Long,
		entityId: Int,
		cachedHash: String,
		entity: ApiProjectEntity,
	) {
		val computedHash = entity.hash()
		if (cachedHash != computedHash) {
			logger.warn("Repairing stale hash for entity $entityId. Cached: $cachedHash, Computed: $computedHash")
			storyEntityDao.updateEntityHash(owner.id, projectId, entityId.toLong(), computedHash)
		}
	}

	override suspend fun loadEntityHash(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<String> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		val hash = storyEntityDao.getEntityHash(owner.id, projectId, entityId.toLong())
		return if (hash != null) {
			SResult.success(hash)
		} else {
			SResult.failure(EntityNotFound(entityId))
		}
	}

	override suspend fun loadEntityHash(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
	): SResult<String> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		val hash = storyEntityDao.getEntityHash(owner.id, projectId, entityId.toLong(), entityType)
		return if (hash != null) {
			SResult.success(hash)
		} else {
			SResult.failure(EntityNotFound(entityId))
		}
	}

	override suspend fun deleteEntity(
		owner: OwnerId,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		storyEntityDao.deleteEntity(
			userId = owner.id,
			projectId = projectId,
			id = entityId.toLong(),
		)
		deletedEntityDao.markEntityDeleted(
			userId = owner.id,
			projectId = projectId,
			id = entityId.toLong(),
		)
		return SResult.success(Unit)
	}

	override suspend fun getEntityDefs(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		filter: (EntityDefinition) -> Boolean
	): List<EntityDefinition> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		return storyEntityDao.getEntityDefs(
			userId = owner.id,
			projectId = projectId,
		).filter { filter(it) }
	}

	override suspend fun getEntityDefsByType(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type,
	): List<EntityDefinition> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		return storyEntityDao.getEntityDefs(
			userId = owner.id,
			projectId = projectId,
			type = type,
		)
	}

	override suspend fun renameProject(
		owner: OwnerId,
		projectId: ProjectId,
		newProjectName: String
	): Boolean = projectDao.updateProjectName(
		owner = owner,
		projectUuid = projectId,
		newName = newProjectName,
	)

	override suspend fun getProject(owner: OwnerId, projectId: ProjectId): ProjectDefinition? {
		val projectData = projectDao.getProjectData(owner, projectId)
		return if (projectData != null) {
			ProjectDefinition.wrap(
				name = projectData.name,
				uuid = projectData.uuid,
			)
		} else {
			null
		}
	}

	override suspend fun getEntityHashes(
		owner: OwnerId,
		projectDef: ProjectDefinition,
	): List<EntityHash> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		return storyEntityDao.getEntityHashes(owner.id, projectId)
	}

	override suspend fun getEntityHashes(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type,
	): List<EntityHash> {
		val projectId = projectDao.getProjectId(owner, projectDef.uuid)
		return storyEntityDao.getEntityHashes(owner.id, projectId, type)
	}
}