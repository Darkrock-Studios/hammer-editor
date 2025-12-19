package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.serialization.KSerializer

interface ProjectEntityDatasource {
	suspend fun loadProjectSyncData(userId: Long, projectDef: ProjectDefinition): ProjectSyncData
	suspend fun createProject(userId: Long, projectName: String): ProjectDefinition
	suspend fun deleteProject(userId: Long, projectId: ProjectId): SResult<Unit>
	suspend fun checkProjectExists(userId: Long, projectDef: ProjectDefinition): Boolean
	suspend fun checkProjectExists(userId: Long, projectId: ProjectId): Boolean
	suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition?
	suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition?
	suspend fun updateSyncData(
		userId: Long,
		projectDef: ProjectDefinition,
		action: (ProjectSyncData) -> ProjectSyncData
	)

	suspend fun findLastId(userId: Long, projectDef: ProjectDefinition): Int?
	suspend fun findEntityType(
		entityId: Int,
		userId: Long,
		projectDef: ProjectDefinition
	): ApiProjectEntity.Type?

	suspend fun <T : ApiProjectEntity> storeEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entity: T,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<Unit>

	suspend fun <T : ApiProjectEntity> loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<T>

	suspend fun loadEntityHash(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
	): SResult<String>

	suspend fun deleteEntity(
		userId: Long,
		entityType: ApiProjectEntity.Type,
		projectDef: ProjectDefinition,
		entityId: Int
	): SResult<Unit>

	suspend fun getEntityDefs(
		userId: Long,
		projectDef: ProjectDefinition,
		filter: (EntityDefinition) -> Boolean = { true }
	): List<EntityDefinition>

	suspend fun getEntityDefsByType(
		userId: Long,
		projectDef: ProjectDefinition,
		type: ApiProjectEntity.Type
	): List<EntityDefinition>

	suspend fun renameProject(userId: Long, projectId: ProjectId, newProjectName: String): Boolean
}