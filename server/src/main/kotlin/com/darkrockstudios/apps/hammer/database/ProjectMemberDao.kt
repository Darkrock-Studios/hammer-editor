package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetMembers
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class ProjectMemberDao(database: Database) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectMemberQueries

	suspend fun isMember(projectRowId: Long, userId: Long): Boolean = withContext(ioDispatcher) {
		queries.isMember(projectRowId, userId).executeAsOne()
	}

	/** False when the user was not a co-author on the project. */
	suspend fun removeMember(projectRowId: Long, userId: Long): Boolean = withContext(ioDispatcher) {
		queries.deleteMember(projectId = projectRowId, userId = userId).value > 0
	}

	suspend fun getMembers(projectRowId: Long): List<GetMembers> = withContext(ioDispatcher) {
		queries.getMembers(projectRowId).executeAsList()
	}

	suspend fun isMemberEmail(projectRowId: Long, email: String): Boolean = withContext(ioDispatcher) {
		queries.isMemberEmail(projectRowId, email).executeAsOne()
	}

	suspend fun getMemberProjects(userId: Long): List<Project> = withContext(ioDispatcher) {
		queries.getMemberProjects(userId).executeAsList()
	}
}
