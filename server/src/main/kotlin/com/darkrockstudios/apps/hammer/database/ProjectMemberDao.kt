package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class ProjectMemberDao(database: Database) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectMemberQueries

	suspend fun isMember(projectRowId: Long, userId: Long): Boolean = withContext(ioDispatcher) {
		queries.isMember(projectRowId, userId).executeAsOne()
	}
}
