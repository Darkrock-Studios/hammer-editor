package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflict
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
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
 */
class ProjectDataSyncOperation(
	projectDef: ProjectDef,
	private val repository: ProjectDataRepository,
	private val api: ProjectDataApi,
	private val broker: ProjectDataConflictBroker,
	private val globalSettingsRepository: GlobalSettingsRepository,
) : SyncOperation(projectDef) {

	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit,
	): CResult<SyncOperationState> {
		state as IdConflictResolutionState

		val userId = globalSettingsRepository.userIdOrThrow()
		val projectId = state.serverProjectId

		val getResult = api.getProjectData(userId, projectDef.name, projectId)
		val serverDto: ProjectDataDto? = getResult.getOrElse { error ->
			onLog(syncLogE("Project data sync: failed to load remote: ${error.message}", projectDef))
			return CResult.failure(error)
		}

		val stored = repository.load()
		val localHash = ProjectDataHasher.hash(stored.data)
		val lastSyncedHash = stored.lastSyncedHash

		when {
			serverDto == null -> {
				if (stored.data == ProjectData()) return CResult.success(state)
				return upload(userId, projectId, stored.data, originalHash = null, onLog)
					?.let { CResult.success(state) }
					?: CResult.failure(Exception("Project data sync failed"))
			}
			serverDto.hash == localHash -> {
				if (lastSyncedHash != serverDto.hash) {
					repository.updateFromSync(serverDto.data, serverDto.hash)
				}
				onLog(syncLogI("Project data already in sync", projectDef))
				return CResult.success(state)
			}
			lastSyncedHash == localHash -> {
				repository.updateFromSync(serverDto.data, serverDto.hash)
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
		val result = api.uploadProjectData(userId, projectDef.name, projectId, data, originalHash)
		val success = result.getOrNull()
		if (success != null) {
			repository.updateFromSync(success.data, success.hash)
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

		onLog(syncLogW("Project data conflict detected", projectDef))
		broker.reportConflict(
			ProjectDataConflict(
				local = data,
				server = conflict.conflict.server,
				serverHash = conflict.conflict.serverHash,
			)
		)
		val resolved = broker.resolutions.receive()

		val resolveResult = api.uploadProjectData(
			userId = userId,
			projectName = projectDef.name,
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
		repository.updateFromSync(resolvedDto.data, resolvedDto.hash)
		onLog(syncLogI("Project data synced", projectDef))
		return resolvedDto
	}
}
