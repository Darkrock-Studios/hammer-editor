package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.serialization.KSerializer

interface ProjectEntityDatasource {
	suspend fun loadProjectSyncData(owner: OwnerId, projectDef: ProjectDefinition): ProjectSyncData
	/** Null when [projectId] is already in use, by this account or another. */
	suspend fun createProject(owner: OwnerId, projectName: String, projectId: ProjectId): ProjectDefinition?
	suspend fun deleteProject(owner: OwnerId, projectId: ProjectId): SResult<Unit>
	suspend fun checkProjectExists(owner: OwnerId, projectDef: ProjectDefinition): Boolean
	suspend fun checkProjectExists(owner: OwnerId, projectId: ProjectId): Boolean
	suspend fun findProjectByName(owner: OwnerId, projectName: String): ProjectDefinition?
	suspend fun getProject(owner: OwnerId, projectId: ProjectId): ProjectDefinition?
	suspend fun updateSyncData(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	)

	suspend fun findLastId(owner: OwnerId, projectDef: ProjectDefinition): Int?
	suspend fun findEntityType(
		entityId: Int,
		owner: OwnerId,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type?

	suspend fun <T : ApiProjectEntity> storeEntity(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entity: T,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<Unit>

	suspend fun <T : ApiProjectEntity> loadEntity(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<T>

	suspend fun loadEntityHash(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
	): SResult<String>

	/** Like the untyped lookup, but an entity of any other type is [EntityNotFound]. */
	suspend fun loadEntityHash(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
	): SResult<String>

	suspend fun deleteEntity(
		owner: OwnerId,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit>

	suspend fun getEntityDefs(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		filter: (EntityDefinition) -> Boolean = { true }
	): List<EntityDefinition>

	suspend fun getEntityDefsByType(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type
	): List<EntityDefinition>

	/** False when the account already holds another project under [newProjectName]. */
	suspend fun renameProject(owner: OwnerId, projectId: ProjectId, newProjectName: String): Boolean

	/** All of a project's entity ids paired with their cached hashes, in one query, sorted by id. */
	suspend fun getEntityHashes(
		owner: OwnerId,
		projectDef: ProjectDefinition,
	): List<EntityHash>

	/** As [getEntityHashes], but limited to a single entity type. */
	suspend fun getEntityHashes(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type,
	): List<EntityHash>
}