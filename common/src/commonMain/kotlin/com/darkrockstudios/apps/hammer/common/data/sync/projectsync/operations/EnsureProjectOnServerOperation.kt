package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_failure
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_success
import com.darkrockstudios.apps.hammer.sync_log_account_project_deleted_elsewhere
import com.darkrockstudios.apps.hammer.sync_log_account_project_id_reminted
import kotlin.time.Instant

/**
 * Makes sure a project that has never synced exists on the server under its id before the project
 * sync begins. Account sync normally created it already; the create is idempotent, so this only
 * costs a round trip then. A project that has synced before skips it: a server that lost it
 * answers `begin_sync` with 410, which [FetchServerDataOperation] handles.
 */
class EnsureProjectOnServerOperation(
	projectDef: ProjectDef,
	private val syncDataDatasource: SyncDataDatasource,
	private val projectCreateResolver: ProjectCreateResolver,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend (success: Boolean) -> Unit
	): CResult<SyncOperationState> {
		val syncData = syncDataDatasource.loadSyncDataOrNull()
		if (syncData != null && syncData.lastSync != Instant.DISTANT_PAST) {
			return CResult.success(state)
		}

		val outcome = projectCreateResolver.createInOwnSession(projectDef)
		return logCreateOutcome(outcome, projectDef, strRes, onLog)
			?.let { CResult.failure(it) }
			?: CResult.success(state)
	}
}

/**
 * Logs a [ProjectCreateResolver] outcome for project sync. Returns the error to fail the sync
 * with, or null when the project is on the server.
 */
internal suspend fun logCreateOutcome(
	outcome: ServerCreateOutcome,
	projectDef: ProjectDef,
	strRes: StrRes,
	onLog: OnSyncLog,
): Throwable? = when (outcome) {
	is ServerCreateOutcome.Created -> {
		if (outcome.reminted) {
			onLog(syncLogW(strRes.get(Res.string.sync_log_account_project_id_reminted, projectDef.name), projectDef))
		}
		if (!outcome.alreadyExisted) {
			onLog(
				syncLogI(
					strRes.get(Res.string.sync_log_account_project_create_server_success, projectDef.name),
					projectDef
				)
			)
		}
		null
	}

	ServerCreateOutcome.DeletedElsewhere -> {
		val message = strRes.get(Res.string.sync_log_account_project_deleted_elsewhere, projectDef.name)
		onLog(syncLogW(message, projectDef))
		ProjectDeletedElsewhereException(projectDef.name)
	}

	is ServerCreateOutcome.Failed -> {
		onLog(
			syncLogE(
				strRes.get(Res.string.sync_log_account_project_create_server_failure, projectDef.name),
				projectDef
			)
		)
		outcome.error
	}
}

class ProjectDeletedElsewhereException(projectName: String) :
	SyncOperationException("Project was deleted on another device: $projectName")
