package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

open class StoryKudosDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.storyKudosQueries

	open suspend fun kindsForUser(projectId: Long, userId: Long): Set<String> = withContext(ioDispatcher) {
		queries.kindsForUser(projectId, userId).executeAsList().toSet()
	}

	/** Makes [kinds] the giver's exact set of picks for the story. */
	open suspend fun replacePicks(projectId: Long, userId: Long, kinds: Set<String>): Unit =
		withContext(ioDispatcher) {
			queries.transaction {
				queries.lockGiver(userId).executeAsOneOrNull()
				val existing = queries.kindsForUser(projectId, userId).executeAsList().toSet()
				val removed = existing - kinds
				if (removed.isNotEmpty()) queries.deleteKinds(projectId, userId, removed)
				(kinds - existing).forEach { queries.insertKudos(projectId, userId, it) }
			}
		}

	open suspend fun countsForProject(projectId: Long): Map<String, Long> = withContext(ioDispatcher) {
		queries.countsForProject(projectId).executeAsList().associate { it.kind to it.givers }
	}

	open suspend fun giverCountForProject(projectId: Long): Long = withContext(ioDispatcher) {
		queries.giverCountForProject(projectId).executeAsOne()
	}

	open suspend fun isOptedOut(projectId: Long): Boolean = withContext(ioDispatcher) {
		queries.isOptedOut(projectId).executeAsOne()
	}

	open suspend fun setOptedOut(projectId: Long, optedOut: Boolean): Unit = withContext(ioDispatcher) {
		if (optedOut) queries.optOut(projectId) else queries.optIn(projectId)
	}
}
