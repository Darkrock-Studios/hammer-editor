package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

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
}
