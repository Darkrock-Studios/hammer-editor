package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import io.github.aakira.napier.Napier

class PrepareForSyncOperation(
	projectDef: ProjectDef,
	private val entitySynchronizers: EntitySynchronizers,
	private val idRepository: IdRepository,
	private val syncDataRepository: SyncDataRepository,
) : SyncOperation(projectDef) {
	override suspend fun execute(
		state: SyncOperationState,
		onProgress: suspend (Float, SyncLogMessage?) -> Unit,
		onLog: OnSyncLog,
		onConflict: EntityConflictHandler<ApiProjectEntity>,
		onComplete: suspend () -> Unit
	): CResult<SyncOperationState> {
		idRepository.findNextId()

		entitySynchronizers.synchronizers.values.forEach { it.prepareForSync() }

		// Create the sync data if it doesnt exist yet
		if (syncDataRepository.createSyncData()) {
			Napier.i("New sync data file created.")
		}

		return CResult.success(state)
	}
}