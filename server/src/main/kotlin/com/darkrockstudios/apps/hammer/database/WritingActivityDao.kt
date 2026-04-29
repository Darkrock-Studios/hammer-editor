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
				.map { DeviceLogRow(deviceId = it.device_id, content = it.content) }
		}

	suspend fun upsert(
		userId: Long,
		projectId: Long,
		deviceId: String,
		content: String,
	) = withContext(ioDispatcher) {
		queries.upsertDeviceLog(
			userId = userId,
			projectId = projectId,
			deviceId = deviceId,
			content = content,
		)
	}
}

data class DeviceLogRow(val deviceId: String, val content: String)
