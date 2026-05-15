package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Lives in its own bridge object so [com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.SyncOperation.execute]
 * doesn't have to grow a project-data-specific parameter threaded through every operation.
 */
class ProjectDataConflictBroker(
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val _conflicts = Channel<ProjectDataConflict>(Channel.BUFFERED)
	private val _resolutions = Channel<ProjectData>(Channel.BUFFERED)

	val conflicts: ReceiveChannel<ProjectDataConflict> get() = _conflicts
	val resolutions: ReceiveChannel<ProjectData> get() = _resolutions

	suspend fun reportConflict(conflict: ProjectDataConflict) = _conflicts.send(conflict)
	fun resolve(resolved: ProjectData) {
		_resolutions.trySend(resolved)
	}
}

data class ProjectDataConflict(
	val local: ProjectData,
	val server: ProjectData,
	val serverHash: String,
)
