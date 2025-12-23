package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

data class PublicProjectInfo(
	val projectUuid: String,
	val userId: Long,
	val projectName: String,
	val penName: String,
	val expiresAt: String?
)

class ProjectAccessDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectAccessQueries

	suspend fun getAccessForProject(projectId: Long): Project_access? = withContext(ioDispatcher) {
		queries.getAccessForProject(projectId).executeAsOneOrNull()
	}

	suspend fun updateAccess(
		projectId: Long,
		password: String?,
		expiresAt: String?
	) {
		withContext(ioDispatcher) {
			queries.updateAccess(projectId, password, expiresAt)
		}
	}

	suspend fun deleteAccess(projectId: Long) {
		withContext(ioDispatcher) {
			queries.deleteAccess(projectId)
		}
	}

	suspend fun deleteAllAccessForUser(userId: Long) {
		withContext(ioDispatcher) {
			queries.deleteAllAccessForUser(userId)
		}
	}

	suspend fun findPublicProjectByPenNameAndProjectName(
		penName: String,
		projectName: String
	): PublicProjectInfo? = withContext(ioDispatcher) {
		queries.findPublicProjectByPenNameAndProjectName(penName, projectName)
			.executeAsOneOrNull()
			?.let {
				PublicProjectInfo(
					projectUuid = it.project_uuid,
					userId = it.user_id,
					projectName = it.project_name,
					penName = it.pen_name ?: "",
					expiresAt = it.expires_at
				)
			}
	}
}
