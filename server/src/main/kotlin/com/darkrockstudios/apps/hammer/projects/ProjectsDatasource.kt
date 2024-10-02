package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import kotlinx.datetime.Instant

interface ProjectsDatasource {
	suspend fun saveSyncData(userId: Long, data: ProjectsSyncData)
	suspend fun getProjects(userId: Long): Set<ProjectDefinition>
	suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition?
	suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition?
	suspend fun loadSyncData(userId: Long): ProjectsSyncData
	suspend fun createUserData(userId: Long)

	suspend fun updateSyncData(
		userId: Long,
		action: (ProjectsSyncData) -> ProjectsSyncData
	): ProjectsSyncData {
		val syncData = loadSyncData(userId)
		val updated = action(syncData)
		saveSyncData(userId, updated)
		return updated
	}

	companion object {
		fun defaultUserData(userId: Long): ProjectsSyncData {
			return ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)
		}
	}
}