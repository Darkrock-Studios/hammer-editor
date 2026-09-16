package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent

class RepositoryUnsyncedContentSource(
	private val ideasRepository: IdeasRepository,
	private val ideasSyncDatasource: IdeasSyncDatasource,
) : UnsyncedContentSource, KoinComponent {

	override suspend fun pendingIn(projectDef: ProjectDef): Int {
		var pending = 0
		temporaryProjectTask(projectDef) { projectScope ->
			pending = projectScope.get<SyncJournal>().pendingEntityCount()
		}
		return pending
	}

	/** An idea with no baseline has never been accepted by the server. */
	override suspend fun pendingIdeas(): Int {
		val syncData = ideasSyncDatasource.load()
		val ideas = ideasRepository.ideasFlow.first()
		return ideas.count { it.id !in syncData.baselines } + syncData.pendingDeletes.size
	}
}
