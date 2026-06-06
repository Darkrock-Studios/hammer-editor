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

	suspend fun markEntityAsDirty(id: Int, oldHash: String) {
		if (globalSettingsStore.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val newSyncData = syncData.copy(
			dirty = syncData.dirty + EntityOriginalState(id, oldHash)
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
		val newSyncData = if (syncData.newIds.contains(deletedId)) {
			// Brand-new entity the server never saw: drop the claim so it can't
			// become a phantom newId. Nothing to tell the server to delete.
			syncData.copy(newIds = syncData.newIds - deletedId)
		} else {
			syncData.copy(deletedIds = syncData.deletedIds + deletedId)
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