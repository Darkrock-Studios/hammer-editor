package com.darkrockstudios.apps.hammer.wear.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn

/** Pushes a fresh capture to the server as soon as the watch has a network. */
interface CaptureSyncScheduler {
	fun syncSoon()
}

class WorkManagerCaptureSyncScheduler(
	private val workManager: WorkManager,
	private val globalSettingsStore: GlobalSettingsStore,
) : CaptureSyncScheduler {

	override fun syncSoon() {
		if (!globalSettingsStore.serverSettings.isSignedIn()) return
		if (!globalSettingsStore.globalSettings.automaticSyncing) return

		val request = OneTimeWorkRequestBuilder<CaptureSyncWorker>()
			.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
			.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
			.build()
		// REPLACE, so a burst of captures on a run costs one upload rather than one each.
		workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
	}

	private companion object {
		const val WORK_NAME = "hammer_capture_sync"
	}
}
