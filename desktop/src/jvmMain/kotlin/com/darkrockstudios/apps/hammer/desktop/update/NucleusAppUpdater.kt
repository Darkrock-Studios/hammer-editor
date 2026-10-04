package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val PERCENT_MAX = 100.0

/**
 * [AppUpdater] over the Nucleus updater runtime. [start] checks once shortly after launch and
 * then on a schedule while the user allows it; a failed check is logged and never shown.
 * [update] downloads, verifies, and hands the file to [install], which by default quits the app
 * and relaunches the new version.
 */
class NucleusAppUpdater(
	private val updater: NucleusUpdater,
	private val installable: Boolean,
	private val preferences: UpdatePreferences,
	private val scope: CoroutineScope,
	private val ioDispatcher: CoroutineContext,
	private val initialDelay: Duration = 10.seconds,
	private val checkInterval: Duration = 6.hours,
	private val install: (File) -> Unit = updater::installAndRestart,
) : AppUpdater {

	private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
	override val state: StateFlow<AppUpdateState> = _state.asStateFlow()

	private val checking = Mutex()
	private var available: UpdateInfo? = null
	private var updateJob: Job? = null

	fun start() {
		scope.launch {
			delay(initialDelay)
			while (isActive) {
				if (preferences.automaticChecks) check()
				delay(checkInterval)
			}
		}
	}

	override fun checkNow() {
		scope.launch { check() }
	}

	private suspend fun check() {
		if (updateJob?.isActive == true) return
		if (!checking.tryLock()) return
		try {
			_state.value = AppUpdateState.Checking
			val result = withContext(ioDispatcher) { updater.checkForUpdates() }
			_state.value = when (result) {
				is UpdateResult.Available -> {
					available = result.info
					availableState(result.info)
				}

				UpdateResult.NotAvailable -> {
					available = null
					AppUpdateState.UpToDate
				}

				is UpdateResult.Error -> {
					Napier.w("Update check failed", result.exception)
					available?.let(::availableState) ?: AppUpdateState.Idle
				}
			}
		} finally {
			checking.unlock()
		}
	}

	private fun availableState(info: UpdateInfo) = AppUpdateState.Available(
		version = info.version,
		installable = installable,
		dismissed = preferences.dismissedVersion == info.version,
	)

	override fun update() {
		val info = available ?: return
		if (!installable || updateJob?.isActive == true) return
		updateJob = scope.launch {
			try {
				var downloaded: File? = null
				updater.downloadUpdate(info).collect { progress ->
					downloaded = progress.file ?: downloaded
					_state.value = AppUpdateState.Downloading(
						version = info.version,
						fraction = (progress.percent / PERCENT_MAX).toFloat().coerceIn(0f, 1f),
					)
				}
				val installer = checkNotNull(downloaded) { "Download finished without a file" }
				_state.value = AppUpdateState.Installing(info.version)
				withContext(ioDispatcher) { install(installer) }
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.w("Update to ${info.version} failed", e)
				_state.value = AppUpdateState.Failed(info.version, e.message ?: e::class.simpleName.orEmpty())
			}
		}
	}

	override fun dismiss() {
		val current = _state.value as? AppUpdateState.Available ?: return
		_state.value = current.copy(dismissed = true)
		scope.launch { preferences.dismiss(current.version) }
	}
}
