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
		// Thar be dragons: REPLACE cancels work that is already RUNNING, so a second capture would
		// abort the first one's sync partway through the protocol, leaving a begun-but-never-ended
		// session on the server. Captures arriving faster than a sync completes would then never
		// upload at all. APPEND_OR_REPLACE queues behind the running sync instead.
		workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
	}

	private companion object {
		const val WORK_NAME = "hammer_capture_sync"
	}
}
