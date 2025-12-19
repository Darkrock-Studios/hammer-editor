package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class DeletedProjectDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.deletedProjectQueries

	suspend fun recordProjectDeleted(
		userId: Long,
		projectId: ProjectId,
	) = withContext(ioDispatcher) {
		val alreadyDeleted = queries.hasDeletedProject(
			userId = userId,
			uuid = projectId.id,
		).executeAsOne()

		if (alreadyDeleted.not()) {
			queries.addDeletedProject(
				userId = userId,
				uuid = projectId.id,
			)
		}
	}
}