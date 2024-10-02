package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Deleted_entity
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class DeletedEntityDao(database: Database) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.deletedEntityQueries

	suspend fun getDeletedEntities(userId: Long, projectId: Long): List<Deleted_entity> =
		withContext(ioDispatcher) {
			queries.getDeletedEntities(userId, projectId).executeAsList()
		}

	suspend fun isDeleted(userId: Long, projectId: Long, id: Long): Boolean =
		withContext(ioDispatcher) {
			queries.checkIsDeleted(userId, projectId, id).executeAsOne()
		}

	fun markEntityDeleted(userId: Long, projectId: Long, id: Long) {
		queries.markEntityDeleted(userId, projectId, id)
	}
}