package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_failure
import com.darkrockstudios.apps.hammer.sync_log_account_project_create_server_success

class EnsureProjectIdOperation(
	projectDef: ProjectDef,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val serverProjectsApi: ServerProjectsApi,
	private val projectsRepository: ProjectsRepository,
	private val strRes: StrRes,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		val metadata = projectMetadataDatasource.loadMetadata(projectDef)
		if (metadata.info.serverProjectId == null) {
			onLog(syncLogI("Project ID missing, creating on server...", projectDef))

			val beginSyncResult = serverProjectsApi.beginProjectsSync()
			if (beginSyncResult.isSuccess) {
				val syncId = beginSyncResult.getOrThrow().syncId
				val createResult = serverProjectsApi.createProject(projectDef.name, syncId)

				if (createResult.isSuccess) {
					val projectId = createResult.getOrThrow().projectId
					projectsRepository.setProjectId(projectDef, projectId)
					onLog(
						syncLogI(
							strRes.get(
								Res.string.sync_log_account_project_create_server_success,
								projectDef.name
							), projectDef
						)
					)
				} else {
					serverProjectsApi.endProjectsSync(syncId)
					val exception = createResult.exceptionOrNull() ?: Exception("Failed to create project on server")
					onLog(
						syncLogE(
							strRes.get(
								Res.string.sync_log_account_project_create_server_failure,
								projectDef.name
							), projectDef
						)
					)
					return CResult.failure(exception)
				}
				serverProjectsApi.endProjectsSync(syncId)
			} else {
				val exception = beginSyncResult.exceptionOrNull() ?: Exception("Failed to begin projects sync")
				onLog(syncLogE(exception.message ?: "Failed to begin projects sync", projectDef))
				return CResult.failure(exception)
			}
		}

		return CResult.success(state)
	}
}
