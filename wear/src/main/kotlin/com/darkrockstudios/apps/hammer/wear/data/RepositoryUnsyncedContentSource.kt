package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.loadPendingEntityCount
import kotlinx.serialization.json.Json
import okio.FileSystem

/**
 * Both counts read their bookkeeping files directly. Neither opens a project scope or waits on a
 * repository's cache: counting is on the path between saving a capture and telling the user it is
 * safe, so it has to be cheap and it must not be able to hang.
 */
class RepositoryUnsyncedContentSource(
	private val ideasDatasource: IdeasDatasource,
	private val ideasSyncDatasource: IdeasSyncDatasource,
	private val fileSystem: FileSystem,
	private val json: Json,
) : UnsyncedContentSource {

	override suspend fun pendingIn(projectDef: ProjectDef): Int =
		loadPendingEntityCount(projectDef, fileSystem, json)

	/** An idea with no baseline has never been accepted by the server. */
	override suspend fun pendingIdeas(): Int {
		val syncData = ideasSyncDatasource.load()
		val ideas = ideasDatasource.loadIdeas()
		return ideas.count { it.id !in syncData.baselines } + syncData.pendingDeletes.size
	}
}
