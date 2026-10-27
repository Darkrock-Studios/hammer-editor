package com.darkrockstudios.apps.hammer.android.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.isSignedIn
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncSetting
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.NightlySyncWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.toJavaDuration

/**
 * Keeps one nightly sync queued while the setting is on and an account is signed in.
 *
 * Each night is its own one-time request that queues the next when it finishes. A periodic
 * request would drift: once constraints hold it back past the window, every later run lands at
 * that same wrong hour.
 */
class NightlySyncScheduler(
	private val workManager: WorkManager,
	private val nightlySyncSetting: NightlySyncSetting,
	private val globalSettingsStore: GlobalSettingsStore,
	private val appScope: CoroutineScope,
	private val clock: Clock,
) {
	fun start() {
		appScope.launch {
			combine(
				nightlySyncSetting.enabled,
				globalSettingsStore.serverSettingsUpdates,
			) { enabled, server -> enabled && server.isSignedIn() }
				.distinctUntilChanged()
				.collect { enabled ->
					if (enabled) {
						enqueue(ExistingWorkPolicy.KEEP)
					} else {
						workManager.cancelUniqueWork(WORK_NAME)
					}
				}
		}
	}

	/** Called by the running worker, so the next night queues behind it instead of cancelling it. */
	fun scheduleNextNight() = enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)

	private fun enqueue(policy: ExistingWorkPolicy) {
		val delay = NightlySyncWindow.untilNextStart(clock.now(), TimeZone.currentSystemDefault())
		val constraints = Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.setRequiresCharging(true)
			.build()
		val request = OneTimeWorkRequestBuilder<NightlySyncWorker>()
			.setInitialDelay(delay.toJavaDuration())
			.setConstraints(constraints)
			.setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
			.build()
		workManager.enqueueUniqueWork(WORK_NAME, policy, request)
	}

	private companion object {
		const val WORK_NAME = "hammer_nightly_sync"
		const val RETRY_BACKOFF_MINUTES = 30L
	}
}
