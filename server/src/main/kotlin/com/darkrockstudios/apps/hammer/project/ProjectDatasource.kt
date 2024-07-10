package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.serialization.KSerializer

interface ProjectDatasource {
	fun loadProjectSyncData(userId: Long, projectDef: ProjectDefinition): ProjectSyncData
	fun createProject(userId: Long, projectDef: ProjectDefinition)
	fun deleteProject(userId: Long, projectName: String): Result<Unit>
	fun checkProjectExists(userId: Long, projectDef: ProjectDefinition): Boolean
	fun updateSyncData(
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
	): SResult<Boolean>

	suspend fun <T : ApiProjectEntity> loadEntity(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int,
		entityType: ApiProjectEntity.Type,
		serializer: KSerializer<T>,
	): SResult<T>

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
}