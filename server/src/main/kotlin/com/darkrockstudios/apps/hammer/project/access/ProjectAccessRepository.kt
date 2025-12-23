package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import kotlin.time.Clock

sealed class PublicProjectResult {
	data class Success(
		val userId: Long,
		val projectUuid: ProjectId,
		val projectName: String,
		val penName: String
	) : PublicProjectResult()

	data object NotFound : PublicProjectResult()
}

class ProjectAccessRepository(
	private val projectAccessDao: ProjectAccessDao,
	private val projectDao: ProjectDao,
	private val clock: Clock,
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

	suspend fun deleteAccess(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.deleteAccess(projectId)
	}

	suspend fun isPublished(userId: Long, projectUuid: ProjectId): Boolean {
		val access = getAccessForProject(userId, projectUuid)
		// Published means: has a record with null password and null expiry
		return access != null && access.access_password == null && access.expires_at == null
	}

	suspend fun deleteAllAccessForUser(userId: Long) {
		projectAccessDao.deleteAllAccessForUser(userId)
	}

	suspend fun findPublicProject(penName: String, projectName: String): PublicProjectResult {
		val info = projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName)
			?: return PublicProjectResult.NotFound

		// Check expiration if set
		if (info.expiresAt != null) {
			val expiresAtInstant = sqliteDateTimeStringToInstant(info.expiresAt)
			if (clock.now() > expiresAtInstant) {
				return PublicProjectResult.NotFound
			}
		}

		return PublicProjectResult.Success(
			userId = info.userId,
			projectUuid = ProjectId(info.projectUuid),
			projectName = info.projectName,
			penName = info.penName
		)
	}
}
