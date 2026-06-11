package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProjectDataRepository(
	private val datasource: ProjectDataDatasource,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	// Lazy to avoid eager construction of the sync journal for projects that never sync.
	private val syncDataDatasource: SyncDataDatasource by projectInject()

	private val mutex = Mutex()
	private val _state = MutableStateFlow<StoredProjectData?>(null)
	val state: StateFlow<StoredProjectData?> = _state.asStateFlow()

	suspend fun load(): StoredProjectData = mutex.withLock {
		val cached = _state.value
		if (cached != null) return@withLock cached
		val loaded = datasource.load()
		_state.value = loaded
		loaded
	}

	/** Persist a user edit. Does not touch [StoredProjectData.lastSyncedHash]. */
	suspend fun updateData(transform: (ProjectData) -> ProjectData) = mutex.withLock {
		val current = _state.value ?: datasource.load()
		val nextData = transform(current.data)
		if (nextData == current.data) return@withLock
		val next = current.copy(data = nextData)
		datasource.save(next)
		_state.value = next
		// Project data is part of the project-wide hash; invalidate so the probe re-syncs it.
		syncDataDatasource.invalidateProjectHash()
	}

	/**
	 * Sync-only: replace both [ProjectData] and [StoredProjectData.lastSyncedHash]
	 * atomically, e.g. after a successful upload or after fast-forwarding to
	 * the server's state.
	 */
	suspend fun updateFromSync(data: ProjectData, lastSyncedHash: String) = mutex.withLock {
		val current = _state.value
		val next = StoredProjectData(data = data, lastSyncedHash = lastSyncedHash)
		if (current == next) return@withLock
		datasource.save(next)
		_state.value = next
	}
}
