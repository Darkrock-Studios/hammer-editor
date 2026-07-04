package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDataDao
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.util.logging.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import kotlin.time.Clock

/**
 * Project data is stored the same way entity content is: an opaque blob plus a client-supplied
 * hash. The server never decodes the client's `ProjectData` shape, so adding a field to it is a
 * client-only change ("Server storage is shape-agnostic" in docs/SYNCING-PROTOCOL.md). The only
 * decode here is the legacy fallback for clients that don't send a content hash.
 */
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
	): SResult<RawProjectDataDto?> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))

		val row = projectDataDao.get(userId, projectId)
			?: return SResult.success(null)

		val data = parseContent(row.content, userId, projectDef)
			?: return SResult.success(null)
		return SResult.success(RawProjectDataDto(data = data, hash = row.hash))
	}

	suspend fun save(
		userId: Long,
		projectDef: ProjectDefinition,
		data: JsonElement,
		originalHash: String?,
		clientHash: String?,
	): SResult<ProjectDataSaveResult> {
		val projectId = projectDao.getProjectIdOrNull(userId, projectDef.uuid)
			?: return SResult.failure(ProjectNotFound(projectDef))

		val existingRow = projectDataDao.get(userId, projectId)
		if (existingRow != null && existingRow.hash != originalHash) {
			val serverData = parseContent(existingRow.content, userId, projectDef)
				?: JsonObject(emptyMap())
			return SResult.success(
				ProjectDataSaveResult.Conflict(
					RawProjectDataConflictDto(server = serverData, serverHash = existingRow.hash),
				),
			)
		}

		// Validation only — the raw element is what gets stored, so fields this server version
		// doesn't know survive; ignoreUnknownKeys makes them pass the decode.
		val decoded = decodeAsProjectData(data)
			?: return SResult.failure(IllegalArgumentException("Payload does not decode as ProjectData"))
		val newHash = clientHash ?: ProjectDataHasher.hash(decoded)
		projectDataDao.upsert(
			userId = userId,
			projectId = projectId,
			content = data.toString(),
			hash = newHash,
			updatedAt = clock.now(),
		)
		return SResult.success(
			ProjectDataSaveResult.Saved(RawProjectDataDto(data = data, hash = newHash)),
		)
	}

	/**
	 * A stored row must both parse as JSON and decode as [ProjectData] (unknown fields tolerated)
	 * or it is treated as missing — so a poisoned row heals itself via the next client upload
	 * instead of failing every device's typed decode forever.
	 */
	private fun parseContent(
		content: String,
		userId: Long,
		projectDef: ProjectDefinition
	): JsonElement? {
		val element = try {
			json.parseToJsonElement(content)
		} catch (e: SerializationException) {
			log.warn(
				"Malformed project_data row for user=$userId project=${projectDef.name}; treating as missing",
				e,
			)
			null
		} ?: return null

		if (decodeAsProjectData(element) == null) {
			log.warn("Undecodable project_data row for user=$userId project=${projectDef.name}; treating as missing")
			return null
		}
		return element
	}

	private fun decodeAsProjectData(data: JsonElement): ProjectData? {
		return try {
			json.decodeFromJsonElement(ProjectData.serializer(), data)
		} catch (e: SerializationException) {
			null
		} catch (e: IllegalArgumentException) {
			null
		}
	}
}

sealed class ProjectDataSaveResult {
	data class Saved(val dto: RawProjectDataDto) : ProjectDataSaveResult()
	data class Conflict(val conflict: RawProjectDataConflictDto) : ProjectDataSaveResult()
}
