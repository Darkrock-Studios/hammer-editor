package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData

interface ProjectsDatasource {
	fun saveSyncData(userId: Long, data: ProjectsSyncData)
	fun getProjects(userId: Long): Set<ProjectDefinition>
	fun loadSyncData(userId: Long): ProjectsSyncData
	fun createUserData(userId: Long)
	fun updateSyncData(
		userId: Long,
		action: (ProjectsSyncData) -> ProjectsSyncData
	): ProjectsSyncData
}