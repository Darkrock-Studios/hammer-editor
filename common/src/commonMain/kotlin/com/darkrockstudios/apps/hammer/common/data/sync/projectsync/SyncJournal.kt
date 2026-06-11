package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity

class SyncJournal(
	private val globalSettingsStore: GlobalSettingsStore,
	private val networkConnectivity: NetworkConnectivity,
	private val datasource: SyncDataDatasource,
) {
	suspend fun needsSync(): Boolean {
		datasource.loadSyncData().apply {
			return (dirty.isNotEmpty() || newIds.isNotEmpty())
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
		val syncData = datasource.loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	/**
	 * Flags an entity as changed since the last sync. The conflict baseline is the hash the server
	 * last confirmed ([ProjectSynchronizationData.syncedHashes]), not a hash re-derived from the
	 * current (already-edited) local state — re-deriving it would let `lastEdited` taint the baseline.
	 */
	suspend fun markEntityAsDirty(id: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		if (syncData.dirty.any { it.id == id }) return

		val baseline = syncData.syncedHashes[id]
		val newSyncData = syncData.copy(
			dirty = syncData.dirty + EntityOriginalState(id, baseline)
		)
		datasource.saveSyncData(newSyncData)
	}

	/**
	 * Records the hash the server now holds for an entity, captured at the moment of a successful
	 * transfer (the exact bytes uploaded or downloaded). This is the only place the conflict
	 * baseline is ever set.
	 */
	suspend fun recordSyncedHash(id: Int, hash: String) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val newSyncData = syncData.copy(
			syncedHashes = syncData.syncedHashes + (id to hash)
		)
		datasource.saveSyncData(newSyncData)
	}

	suspend fun recordNewId(claimedId: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val newSyncData = syncData.copy(newIds = syncData.newIds + claimedId)
		datasource.saveSyncData(newSyncData)
	}

	suspend fun recordIdDeletion(deletedId: Int) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val withoutSyncedHash = syncData.copy(syncedHashes = syncData.syncedHashes - deletedId)
		val newSyncData = if (withoutSyncedHash.newIds.contains(deletedId)) {
			// Brand-new entity the server never saw: drop the claim so it can't
			// become a phantom newId. Nothing to tell the server to delete.
			withoutSyncedHash.copy(newIds = withoutSyncedHash.newIds - deletedId)
		} else {
			withoutSyncedHash.copy(deletedIds = withoutSyncedHash.deletedIds + deletedId)
		}
		datasource.saveSyncData(newSyncData)
	}

	fun isServerSynchronized(): Boolean {
		return globalSettingsStore.serverSettings != null
	}

	suspend fun createSyncData(): Boolean {
		return if (datasource.syncDataExists().not()) {
			val newData = datasource.createSyncData()
			datasource.saveSyncData(newData)
			true
		} else {
			false
		}
	}

	suspend fun loadSyncData() = datasource.loadSyncData()
	fun saveSyncData(syncData: ProjectSynchronizationData) = datasource.saveSyncData(syncData)
}