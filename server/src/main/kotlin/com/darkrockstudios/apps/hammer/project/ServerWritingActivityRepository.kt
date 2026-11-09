package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

/**
 * Server-side dumb storage for per-(project, author, device) writing activity logs.
 * The server never inspects or merges sessions; clients GET everyone's
 * slot, merge their own slot locally, and POST back the merged result.
 */
class ServerWritingActivityRepository(
	private val writingActivityDao: WritingActivityDao,
	private val projectDao: ProjectDao,
	private val json: Json,
	private val log: Logger,
) : KoinComponent {

	suspend fun loadAll(
		owner: OwnerId,
		projectDef: ProjectDefinition,
	): SResult<WritingActivityResponse> {
		val projectId = projectDao.getProjectIdOrNull(owner, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))
		val rows = writingActivityDao.getAllForProject(owner.id, projectId)

		val slots = buildList {
			for (row in rows) {
				val parsed = try {
					json.decodeFromString<DeviceLog>(row.content)
				} catch (e: SerializationException) {
					log.warn(
						"Skipping malformed writing activity log for user=${owner.id} project=${projectDef.name} device=${row.deviceId}",
						e,
					)
					null
				}
				if (parsed != null) {
					val log = parsed.copy(authorId = row.authorId, authorName = row.authorName)
					add(WritingActivitySlot(row.deviceId, log))
				}
			}
		}
		return SResult.success(WritingActivityResponse(slots = slots))
	}

	/** Stores [log] as [authorId]'s slot, replacing whatever author the client sent. */
	suspend fun saveDeviceLog(
		owner: OwnerId,
		projectDef: ProjectDefinition,
		authorId: Long,
		authorName: String?,
		deviceId: String,
		log: DeviceLog,
	): SResult<Unit> {
		val projectId = projectDao.getProjectIdOrNull(owner, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))
		val content = json.encodeToString(log.copy(authorId = authorId, authorName = authorName))
		writingActivityDao.upsert(
			userId = owner.id,
			projectId = projectId,
			authorId = authorId,
			authorName = authorName,
			deviceId = deviceId,
			content = content,
		)
		return SResult.success(Unit)
	}
}
