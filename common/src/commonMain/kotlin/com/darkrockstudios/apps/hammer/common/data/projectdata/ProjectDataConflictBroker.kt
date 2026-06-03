package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Lives in its own bridge object so [com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.SyncOperation.execute]
 * doesn't have to grow a project-data-specific parameter threaded through every operation.
 */
class ProjectDataConflictBroker(
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val _conflicts = Channel<ProjectDataConflict>(Channel.BUFFERED)
	private val _resolutions = Channel<Resolution>(Channel.BUFFERED)

	val conflicts: ReceiveChannel<ProjectDataConflict> get() = _conflicts

	suspend fun reportConflict(conflict: ProjectDataConflict) = _conflicts.send(conflict)
	fun resolve(resolved: ProjectData) {
		_resolutions.trySend(Resolution.Resolved(resolved))
	}

	/**
	 * Abort an in-flight conflict, failing the sync instead of waiting for an interactive
	 * resolution. Used by flows without a resolver UI (bulk account sync), where a project-data
	 * conflict would otherwise hang the project forever in [awaitResolution]. Delivers an abort
	 * sentinel rather than closing the channels, so this project-scoped singleton stays reusable
	 * for any later sync in the same scope.
	 */
	fun abort() {
		_resolutions.trySend(Resolution.Aborted)
	}

	/**
	 * Suspends until the reported conflict is [resolve]d or [abort]ed. Throws
	 * [ProjectDataConflictUnresolvedException] on abort so the sync reports failure.
	 */
	suspend fun awaitResolution(): ProjectData {
		return when (val resolution = _resolutions.receive()) {
			is Resolution.Resolved -> resolution.data
			Resolution.Aborted -> throw ProjectDataConflictUnresolvedException()
		}
	}

	private sealed interface Resolution {
		data class Resolved(val data: ProjectData) : Resolution
		data object Aborted : Resolution
	}
}

class ProjectDataConflictUnresolvedException :
	Exception("Project data conflict could not be resolved")

data class ProjectDataConflict(
	val local: ProjectData,
	val server: ProjectData,
	val serverHash: String,
)
