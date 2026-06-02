package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDataDao
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import kotlin.time.Clock

class ServerProjectDataRepository(
	private val projectDataDao: ProjectDataDao,
	private val projectDao: ProjectDao,
	private val json: Json,
	private val clock: Clock,
	private val log: Logger,
) : KoinComponent {

	suspend fun load(
		userId: Long,
		projectDef: ProjectDefinition,
	): SResult<ProjectDataDto?> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))

		val row = projectDataDao.get(userId, projectId)
			?: return SResult.success(null)

		val data = try {
			json.decodeFromString<ProjectData>(row.content)
		} catch (e: SerializationException) {
			log.warn(
				"Malformed project_data row for user=$userId project=${projectDef.name}; treating as missing",
				e,
			)
			return SResult.success(null)
		}
		return SResult.success(ProjectDataDto(data = data, hash = row.hash))
	}

	suspend fun save(
		userId: Long,
		projectDef: ProjectDefinition,
		data: ProjectData,
		originalHash: String?,
	): SResult<ProjectDataSaveResult> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))

		val existingRow = projectDataDao.get(userId, projectId)
		if (existingRow != null && existingRow.hash != originalHash) {
			val serverData = try {
				json.decodeFromString<ProjectData>(existingRow.content)
			} catch (e: SerializationException) {
				log.warn(
					"Malformed project_data row for user=$userId project=${projectDef.name} during conflict",
					e,
				)
				ProjectData()
			}
			return SResult.success(
				ProjectDataSaveResult.Conflict(
					ProjectDataConflictDto(server = serverData, serverHash = existingRow.hash),
				),
			)
		}

		val newHash = ProjectDataHasher.hash(data)
		val content = json.encodeToString(data)
		projectDataDao.upsert(
			userId = userId,
			projectId = projectId,
			content = content,
			hash = newHash,
			updatedAt = clock.now(),
		)
		return SResult.success(
			ProjectDataSaveResult.Saved(ProjectDataDto(data = data, hash = newHash)),
		)
	}
}

sealed class ProjectDataSaveResult {
	data class Saved(val dto: ProjectDataDto) : ProjectDataSaveResult()
	data class Conflict(val conflict: ProjectDataConflictDto) : ProjectDataSaveResult()
}
