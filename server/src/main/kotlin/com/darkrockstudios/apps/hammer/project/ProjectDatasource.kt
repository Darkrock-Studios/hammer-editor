package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

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

	fun getEntityType(
		userId: Long,
		projectDef: ProjectDefinition,
		entityId: Int
	): ApiProjectEntity.Type?
}