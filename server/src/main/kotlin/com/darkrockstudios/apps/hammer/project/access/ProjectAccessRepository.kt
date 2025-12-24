package com.darkrockstudios.apps.hammer.project.access

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectAccessDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.time.Clock

sealed class PublicProjectResult {
	data class Success(
		val userId: Long,
		val projectUuid: ProjectId,
		val projectName: String,
		val penName: String
	) : PublicProjectResult()

	data object NotFound : PublicProjectResult()

	data object PasswordRequired : PublicProjectResult()
}

data class AccessEntryInfo(
	val id: Long,
	val password: String?,
	val expiresAt: String?,
	val expiresAtFormatted: String?,
	val isExpired: Boolean
)

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

	suspend fun deleteAccessById(userId: Long, projectUuid: ProjectId, accessId: Long): Boolean {
		// Verify the user owns this project first
		projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.deleteAccessById(accessId)
		return true
	}

	suspend fun isPublished(userId: Long, projectUuid: ProjectId): Boolean {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val access = projectAccessDao.getPublicAccessForProject(projectId)
		// Published means: has a record with null password
		return access != null
	}

	suspend fun hasAnyAccess(userId: Long, projectUuid: ProjectId): Boolean {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val allAccess = projectAccessDao.getAllAccessForProject(projectId)
		return allAccess.isNotEmpty()
	}

	suspend fun publish(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		// Only create public access if not already published
		val existing = projectAccessDao.getPublicAccessForProject(projectId)
		if (existing == null) {
			projectAccessDao.insertAccess(projectId, null, null)
		}
	}

	suspend fun unpublish(userId: Long, projectUuid: ProjectId) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.deletePublicAccessForProject(projectId)
	}

	suspend fun createPrivateAccess(
		userId: Long,
		projectUuid: ProjectId,
		password: String,
		expiresAt: String?
	) {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		projectAccessDao.insertAccess(projectId, password, expiresAt)
	}

	suspend fun getPrivateAccessEntries(userId: Long, projectUuid: ProjectId): List<AccessEntryInfo> {
		val projectId = projectDao.getProjectId(userId, projectUuid)
		val entries = projectAccessDao.getPrivateAccessForProject(projectId)
		val now = clock.now()

		return entries.map { entry ->
			val isExpired = entry.expires_at?.let {
				val expiresAtInstant = sqliteDateTimeStringToInstant(it)
				now > expiresAtInstant
			} ?: false

			val formattedDate = entry.expires_at?.let {
				try {
					val instant = sqliteDateTimeStringToInstant(it)
					val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
					val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(ZoneId.systemDefault())
					formatter.format(zoned)
				} catch (e: Exception) {
					it
				}
			}

			AccessEntryInfo(
				id = entry.id,
				password = entry.access_password,
				expiresAt = entry.expires_at,
				expiresAtFormatted = formattedDate,
				isExpired = isExpired
			)
		}
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

	suspend fun findAccessibleProject(
		penName: String,
		projectName: String,
		password: String?
	): PublicProjectResult {
		// First check for public access (no password required)
		val publicInfo = projectAccessDao.findPublicProjectByPenNameAndProjectName(penName, projectName)
		if (publicInfo != null) {
			// Check expiration if set
			if (publicInfo.expiresAt != null) {
				val expiresAtInstant = sqliteDateTimeStringToInstant(publicInfo.expiresAt)
				if (clock.now() > expiresAtInstant) {
					// Public access expired, continue to check password access
				} else {
					return PublicProjectResult.Success(
						userId = publicInfo.userId,
						projectUuid = ProjectId(publicInfo.projectUuid),
						projectName = publicInfo.projectName,
						penName = publicInfo.penName
					)
				}
			} else {
				return PublicProjectResult.Success(
					userId = publicInfo.userId,
					projectUuid = ProjectId(publicInfo.projectUuid),
					projectName = publicInfo.projectName,
					penName = publicInfo.penName
				)
			}
		}

		// Check if any access exists at all
		val hasAnyAccess = projectAccessDao.hasAnyAccessForProject(penName, projectName)
		if (!hasAnyAccess) {
			return PublicProjectResult.NotFound
		}

		// If no password provided but there's private access available, require password
		if (password.isNullOrBlank()) {
			return PublicProjectResult.PasswordRequired
		}

		// Check password-protected access
		val passwordInfo = projectAccessDao.findProjectByPenNameProjectNameAndPassword(penName, projectName, password)
			?: return PublicProjectResult.PasswordRequired

		// Check expiration if set
		if (passwordInfo.expiresAt != null) {
			val expiresAtInstant = sqliteDateTimeStringToInstant(passwordInfo.expiresAt)
			if (clock.now() > expiresAtInstant) {
				return PublicProjectResult.PasswordRequired
			}
		}

		return PublicProjectResult.Success(
			userId = passwordInfo.userId,
			projectUuid = ProjectId(passwordInfo.projectUuid),
			projectName = passwordInfo.projectName,
			penName = passwordInfo.penName
		)
	}
}
