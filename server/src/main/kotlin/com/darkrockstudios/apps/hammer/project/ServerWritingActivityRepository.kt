package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

/**
 * Server-side dumb storage for per-(project, device) writing activity logs.
 * The server never inspects or merges contents — clients GET everyone's
 * slot, merge their own slot locally, and POST back the merged result.
 */
class ServerWritingActivityRepository(
	private val writingActivityDao: WritingActivityDao,
	private val projectDao: ProjectDao,
	private val json: Json,
	private val log: Logger,
) : KoinComponent {

	suspend fun loadAll(
		userId: Long,
		projectDef: ProjectDefinition,
	): SResult<WritingActivityResponse> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))
		val rows = writingActivityDao.getAllForProject(userId, projectId)

		val devices = buildMap {
			for (row in rows) {
				val parsed = try {
					json.decodeFromString<DeviceLog>(row.content)
				} catch (e: SerializationException) {
					log.warn(
						"Skipping malformed writing activity log for user=$userId project=${projectDef.name} device=${row.deviceId}",
						e,
					)
					null
				}
				if (parsed != null) put(row.deviceId, parsed)
			}
		}
		return SResult.success(WritingActivityResponse(devices = devices))
	}

	suspend fun saveDeviceLog(
		userId: Long,
		projectDef: ProjectDefinition,
		deviceId: String,
		log: DeviceLog,
	): SResult<Unit> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))
		val content = json.encodeToString(log)
		writingActivityDao.upsert(userId, projectId, deviceId, content)
		return SResult.success(Unit)
	}
}
