package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class DeletedIdeaDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.deletedIdeaQueries

	suspend fun getDeletedIdeas(userId: Long): Set<IdeaId> =
		withContext(ioDispatcher) {
			queries.getDeletedIdeas(userId)
				.executeAsList()
				.map { IdeaId(it) }
				.toSet()
		}

	suspend fun recordIdeaDeleted(userId: Long, ideaId: IdeaId) = withContext(ioDispatcher) {
		queries.addDeletedIdea(userId = userId, uuid = ideaId.id)
	}

	suspend fun isIdeaDeleted(userId: Long, ideaId: IdeaId): Boolean =
		withContext(ioDispatcher) {
			queries.hasDeletedIdea(userId = userId, uuid = ideaId.id).executeAsOne()
		}
}
