package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.QueryResult
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

open class StoryKudosDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.storyKudosQueries
	private val driver = database.driver

	open suspend fun kindsForUser(projectId: Long, userId: Long): Set<String> = withContext(ioDispatcher) {
		queries.kindsForUser(projectId, userId).executeAsList().toSet()
	}

	/** Makes [kinds] the giver's exact set of picks for the story. */
	open suspend fun replacePicks(projectId: Long, userId: Long, kinds: Set<String>): Unit =
		withContext(ioDispatcher) {
			queries.transaction {
				lockGiver(userId)
				val existing = queries.kindsForUser(projectId, userId).executeAsList().toSet()
				val removed = existing - kinds
				if (removed.isNotEmpty()) queries.deleteKinds(projectId, userId, removed)
				(kinds - existing).forEach { queries.insertKudos(projectId, userId, it) }
			}
		}

	/**
	 * Serializes one giver's concurrent updates so the per-group caps hold. Advisory rather than
	 * a row lock so the giver's other writes never wait; issued raw because SQLDelight's
	 * Postgres dialect can't parse the call.
	 */
	private fun lockGiver(userId: Long) {
		driver.executeQuery(
			identifier = null,
			sql = "SELECT pg_advisory_xact_lock(hashtextextended('story_kudos', ?))",
			mapper = { QueryResult.Value(Unit) },
			parameters = 1,
		) { bindLong(0, userId) }
	}

	open suspend fun countsForProject(projectId: Long, kinds: Collection<String>): Map<String, Long> =
		withContext(ioDispatcher) {
			queries.countsForProject(projectId, kinds).executeAsList().associate { it.kind to it.givers }
		}

	open suspend fun giverCountForProject(projectId: Long, kinds: Collection<String>): Long =
		withContext(ioDispatcher) {
			queries.giverCountForProject(projectId, kinds).executeAsOne()
		}

	open suspend fun isOptedOut(projectId: Long): Boolean = withContext(ioDispatcher) {
		queries.isOptedOut(projectId).executeAsOne()
	}

	open suspend fun setOptedOut(projectId: Long, optedOut: Boolean): Unit = withContext(ioDispatcher) {
		if (optedOut) queries.optOut(projectId) else queries.optIn(projectId)
	}
}
