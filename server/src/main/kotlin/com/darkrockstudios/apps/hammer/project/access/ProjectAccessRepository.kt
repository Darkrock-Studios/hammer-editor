package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao

class ProjectAccessRepository(
	private val projectAccessDao: ProjectAccessDao,
	private val projectDao: ProjectDao
) {
	suspend fun getAccessForProject(userId: Long, projectUuid: ProjectId): Project_access? {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		return projectAccessDao.getAccessForProject(projectId)
	}

	suspend fun setAccess(
		userId: Long,
		projectUuid: ProjectId,
		password: String? = null,
		expiresAt: String? = null
	) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.updateAccess(projectId, password, expiresAt)
	}
}
