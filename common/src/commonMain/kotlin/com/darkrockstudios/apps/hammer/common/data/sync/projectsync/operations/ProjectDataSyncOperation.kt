package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflict
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.differsOutsideDictionary
import com.darkrockstudios.apps.hammer.common.data.projectdata.mergeDictionaryWords
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import io.github.aakira.napier.Napier

/**
 * A non-conflict server error fails the whole sync — unlike writing-activity sync,
 * the data is user-authored and silent loss is unacceptable.
 *
 * A conflict confined to [ProjectData.dictionaryWords] is merged by union here and never
 * reaches the resolver; any other conflicting field still goes through the broker.
 */
class ProjectDataSyncOperation(
	projectDef: ProjectDef,
	private val repository: ProjectDataRepository,
	private val api: ProjectDataApi,
	private val broker: ProjectDataConflictBroker,
	private val globalSettingsStore: GlobalSettingsStore,
) : SyncOperation(projectDef) {

	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	): CResult<SyncOperationState> {
		state as IdConflictResolutionState

		val userId = globalSettingsStore.userIdOrThrow()
		val projectId = state.serverProjectId

		val getResult = api.getProjectData(userId, projectId)
		val serverDto: ProjectDataDto? = getResult.getOrElse { error ->
			onLog(syncLogE("Project data sync: failed to load remote: ${error.message}", projectDef))
			return CResult.failure(error)
		}

		val stored = repository.load()
		val localHash = ProjectDataHasher.hash(stored.data)
		val lastSyncedHash = stored.lastSyncedHash
		// A never-synced project baselines against the default-data hash, matching what the server
		// holds for it. Without this, a fresh download (default local data, null lastSyncedHash)
		// looks like a local edit and gets pushed, colliding with the server's real settings.
		val baseline = lastSyncedHash ?: ProjectDataHasher.hash(ProjectData())

		when {
			serverDto == null -> {
				if (stored.data == ProjectData()) return CResult.success(state)
				return upload(userId, projectId, stored.data, originalHash = null, onLog)
					?.let { CResult.success(state) }
					?: CResult.failure(Exception("Project data sync failed"))
			}
			serverDto.hash == localHash -> {
				if (lastSyncedHash != serverDto.hash) {
					repository.updateFromSync(serverDto.data, serverDto.hash, snapshot = stored.data)
				}
				onLog(syncLogI("Project data already in sync", projectDef))
				return CResult.success(state)
			}
			baseline == localHash -> {
				// Record the hash of the data as THIS build stored it, not the server row's hash.
				// When the row carries a field a newer build added, our typed decode stripped it;
				// recording the server hash would make the stored copy look locally edited, and the
				// next sync would upload it — deleting the newer field server-side. With the stored
				// hash, an out-of-date device just keeps fast-forwarding harmlessly.
				repository.updateFromSync(serverDto.data, ProjectDataHasher.hash(serverDto.data), snapshot = stored.data)
				onLog(syncLogI("Project data updated from server", projectDef))
				return CResult.success(state)
			}
			else -> {
				return upload(userId, projectId, stored.data, originalHash = lastSyncedHash, onLog)
					?.let { CResult.success(state) }
					?: CResult.failure(Exception("Project data sync failed"))
			}
		}
	}

	private suspend fun upload(
		userId: Long,
		projectId: ProjectId,
		data: ProjectData,
		originalHash: String?,
		onLog: OnSyncLog,
	): ProjectDataDto? {
		val result = api.uploadProjectData(userId, projectId, data, originalHash)
		val success = result.getOrNull()
		if (success != null) {
			repository.updateFromSync(success.data, success.hash, snapshot = data)
			onLog(syncLogI("Project data synced", projectDef))
			return success
		}

		val error = result.exceptionOrNull()
		val conflict = error as? ProjectDataConflictException
		if (conflict == null) {
			Napier.e("Project data upload failed", error)
			onLog(syncLogE("Project data upload failed: ${error?.message}", projectDef))
			return null
		}

		val server = conflict.conflict.server
		val resolved = if (data.differsOutsideDictionary(server)) {
			onLog(syncLogW("Project data conflict detected", projectDef))
			broker.reportConflict(
				ProjectDataConflict(
					local = data,
					server = server,
					serverHash = conflict.conflict.serverHash,
				)
			)
			broker.awaitResolution()
		} else {
			onLog(syncLogI("Project data conflict: dictionary words merged", projectDef))
			data.copy(dictionaryWords = mergeDictionaryWords(data, server))
		}

		val resolveResult = api.uploadProjectData(
			userId = userId,
			projectId = projectId,
			data = resolved,
			originalHash = conflict.conflict.serverHash,
		)
		val resolvedDto = resolveResult.getOrNull()
		if (resolvedDto == null) {
			val resolveError = resolveResult.exceptionOrNull()
			Napier.e("Project data conflict resolution failed", resolveError)
			onLog(syncLogE("Project data conflict resolution failed: ${resolveError?.message}", projectDef))
			return null
		}
		repository.updateFromSync(resolvedDto.data, resolvedDto.hash, snapshot = data)
		onLog(syncLogI("Project data synced", projectDef))
		return resolvedDto
	}
}
