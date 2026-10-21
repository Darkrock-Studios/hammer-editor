package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.math.max
import kotlin.time.Instant

/**
 * A project's record of what has changed locally since the server last agreed with it: dirty
 * entities, newly claimed IDs, deleted IDs, and the hashes the server last confirmed. It holds no
 * state in memory; every call reads and rewrites `sync.json` through [SyncJournalDatasource],
 * rebuilding the file from the entities on disk when it is missing or corrupt.
 */
class SyncJournal(
	private val projectDef: ProjectDef,
	private val globalSettingsStore: GlobalSettingsStore,
	private val networkConnectivity: NetworkConnectivity,
	private val datasource: SyncJournalDatasource,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()

	private val idDatasources = IdAllocator.getIdSources(ProjectDefScope(projectDef))

	suspend fun needsSync(): Boolean {
		loadSyncData().apply {
			return (dirty.isNotEmpty() || newIds.isNotEmpty() || verifyAllEntities)
		}
	}

	suspend fun deletedIds(): Set<Int> {
		return datasource.loadSyncDataOrNull()?.deletedIds ?: emptySet()
	}

	suspend fun shouldAutoSync(): Boolean = globalSettingsStore.serverIsSetup() &&
		globalSettingsStore.globalSettings.automaticSyncing &&
		networkConnectivity.hasActiveConnection() &&
		needsSync()

	suspend fun isEntityDirty(id: Int): Boolean {
		val syncData = loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	/**
	 * Flags an entity as changed since the last sync. The conflict baseline is the hash the server
	 * last confirmed ([ProjectSynchronizationData.syncedHashes]), not a hash re-derived from the
	 * current (already-edited) local state — re-deriving it would let `lastEdited` taint the baseline.
	 */
	suspend fun markEntityAsDirty(id: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = loadSyncData()
		if (syncData.dirty.any { it.id == id }) return

		val baseline = syncData.syncedHashes[id]
		saveInvalidatingProjectHash(
			syncData.copy(dirty = syncData.dirty + EntityOriginalState(id, baseline))
		)
	}

	/**
	 * Records the hash the server now holds for an entity, captured at the moment of a successful
	 * transfer (the exact bytes uploaded or downloaded). This is the only place the conflict
	 * baseline is ever set.
	 */
	suspend fun recordSyncedHash(id: Int, hash: String) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = loadSyncData()
		val newSyncData = syncData.copy(
			syncedHashes = syncData.syncedHashes + (id to hash)
		)
		datasource.saveSyncData(newSyncData)
	}

	suspend fun recordNewId(claimedId: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = loadOrCreate(atLeastLastId = claimedId - 1)
		saveInvalidatingProjectHash(syncData.copy(newIds = syncData.newIds + claimedId))
	}

	suspend fun recordIdDeletion(deletedId: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = loadOrCreate(atLeastLastId = deletedId)
		val withoutSyncedHash = syncData.copy(syncedHashes = syncData.syncedHashes - deletedId)
		val newSyncData = if (withoutSyncedHash.newIds.contains(deletedId)) {
			// Brand-new entity the server never saw: drop the claim so it can't
			// become a phantom newId. Nothing to tell the server to delete.
			withoutSyncedHash.copy(newIds = withoutSyncedHash.newIds - deletedId)
		} else {
			withoutSyncedHash.copy(deletedIds = withoutSyncedHash.deletedIds + deletedId)
		}
		saveInvalidatingProjectHash(newSyncData)
	}

	/**
	 * Single chokepoint for content mutations: persists [data] with the cached project-wide hash
	 * cleared in the same write. This keeps the invariant "cached hash present ⟹ no pending local
	 * work" airtight (see SYNCING-PROTOCOL.md) so a new mutation path can't silently leave the probe
	 * able to skip a project that actually changed. Sync-internal writes that must preserve the cache
	 * (e.g. [recordSyncedHash], FinalizeSync) go through [datasource] directly instead.
	 */
	private fun saveInvalidatingProjectHash(data: ProjectSynchronizationData) {
		datasource.saveSyncData(data.copy(cachedProjectHash = null))
	}

	fun isServerSynchronized(): Boolean {
		return globalSettingsStore.serverSettings != null
	}

	suspend fun createSyncData(): Boolean {
		return if (datasource.syncDataExists().not()) {
			datasource.saveSyncData(buildSyncData(atLeastLastId = 0))
			true
		} else {
			false
		}
	}

	suspend fun loadSyncData(): ProjectSynchronizationData = loadOrCreate(atLeastLastId = 0)

	/**
	 * [atLeastLastId] is an ID the caller knows has been claimed. The entities on disk can't
	 * show it when the newest entity has already been deleted, and without it that ID would
	 * be missing from a rebuilt journal's deleted IDs.
	 */
	private suspend fun loadOrCreate(atLeastLastId: Int): ProjectSynchronizationData =
		datasource.loadSyncDataOrNull()
			?: buildSyncData(atLeastLastId).also { datasource.saveSyncData(it) }

	private suspend fun buildSyncData(atLeastLastId: Int): ProjectSynchronizationData {
		// Scans every entity's storage, and callers include the UI's close check
		val entityIds = withContext(ioDispatcher) { IdAllocator.findEntityIds(projectDef, idDatasources) }
		val lastId = max(entityIds.ids.maxOrNull() ?: 0, atLeastLastId)
		// Recording a live entity as deleted would delete it on the server at the next sync,
		// so with unreadable storage no deletions are inferred at all.
		val deletedIds = if (entityIds.complete) {
			(1..lastId).filterNot { it in entityIds.ids }.toSet()
		} else {
			emptySet()
		}

		return ProjectSynchronizationData(
			lastId = lastId,
			newIds = emptyList(),
			lastSync = Instant.DISTANT_PAST,
			dirty = emptyList(),
			deletedIds = deletedIds
		)
	}
	fun saveSyncData(syncData: ProjectSynchronizationData) = datasource.saveSyncData(syncData)

	/** Drops what one specific server confirmed, keeping local work it hasn't seen. */
	fun clearSyncBaseline() = datasource.clearSyncBaseline()
}