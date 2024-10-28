package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity

class SyncDataRepository(
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val networkConnectivity: NetworkConnectivity,
	private val datasource: SyncDataDatasource,
) {
	suspend fun needsSync(): Boolean {
		return datasource.loadSyncData().dirty.isNotEmpty()
	}

	suspend fun deletedIds(): Set<Int> {
		return datasource.loadSyncDataOrNull()?.deletedIds ?: emptySet()
	}

	suspend fun shouldAutoSync(): Boolean = globalSettingsRepository.serverIsSetup() &&
		globalSettingsRepository.globalSettings.automaticSyncing &&
		networkConnectivity.hasActiveConnection() &&
		needsSync()

	suspend fun isEntityDirty(id: Int): Boolean {
		val syncData = datasource.loadSyncData()
		return syncData.dirty.any { it.id == id }
	}

	suspend fun markEntityAsDirty(id: Int, oldHash: String) {
		val syncData = datasource.loadSyncData()
		val newSyncData = syncData.copy(
			dirty = syncData.dirty + EntityOriginalState(id, oldHash)
		)
		datasource.saveSyncData(newSyncData)
	}

	suspend fun recordNewId(claimedId: Int) {
		if (globalSettingsRepository.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val newSyncData = syncData.copy(newIds = syncData.newIds + claimedId)
		datasource.saveSyncData(newSyncData)
	}

	suspend fun recordIdDeletion(deletedId: Int) {
		if (globalSettingsRepository.isServerSynchronized().not()) return

		val syncData = datasource.loadSyncData()
		val updated = syncData.deletedIds + deletedId
		val newSyncData = syncData.copy(deletedIds = updated)
		datasource.saveSyncData(newSyncData)
	}

	fun isServerSynchronized(): Boolean {
		return globalSettingsRepository.serverSettings != null
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