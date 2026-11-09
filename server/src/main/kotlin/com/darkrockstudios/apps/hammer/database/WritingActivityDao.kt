package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class WritingActivityDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.writingActivityQueries

	suspend fun getAllForProject(userId: Long, projectId: Long): List<DeviceLogRow> =
		withContext(ioDispatcher) {
			queries.getAllForProject(userId, projectId)
				.executeAsList()
				.map {
					DeviceLogRow(
						deviceId = it.device_id,
						content = it.content,
						authorId = it.author_id,
						authorName = it.author_name,
					)
				}
		}

	suspend fun upsert(
		userId: Long,
		projectId: Long,
		authorId: Long,
		authorName: String?,
		deviceId: String,
		content: String,
	) = withContext(ioDispatcher) {
		queries.upsertDeviceLog(
			userId = userId,
			projectId = projectId,
			authorId = authorId,
			deviceId = deviceId,
			content = content,
			authorName = authorName,
		)
	}
}

data class DeviceLogRow(
	val deviceId: String,
	val content: String,
	val authorId: Long,
	val authorName: String?,
)
