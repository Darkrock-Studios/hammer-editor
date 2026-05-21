package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class ProjectDataDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectDataQueries

	suspend fun get(userId: Long, projectId: Long): ProjectDataRow? =
		withContext(ioDispatcher) {
			queries.getForProject(userId, projectId)
				.executeAsOneOrNull()
				?.let { ProjectDataRow(content = it.content, hash = it.hash, updatedAt = it.updated_at) }
		}

	suspend fun upsert(
		userId: Long,
		projectId: Long,
		content: String,
		hash: String,
		updatedAt: Instant,
	) = withContext(ioDispatcher) {
		queries.upsert(
			userId = userId,
			projectId = projectId,
			content = content,
			hash = hash,
			updatedAt = updatedAt,
		)
	}
}

data class ProjectDataRow(val content: String, val hash: String, val updatedAt: Instant)
