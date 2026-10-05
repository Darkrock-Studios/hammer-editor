package com.darkrockstudios.apps.hammer.common.data.appupdate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Self-update for the builds that may do it: desktop installs from a GitHub release or the
 * AppImage. Every other build binds [NoOpAppUpdater] and never leaves
 * [AppUpdateState.Unsupported], so store builds carry no update UI.
 */
interface AppUpdater {
	val state: StateFlow<AppUpdateState>

	/** Looks for a newer release now, regardless of the automatic schedule. */
	fun checkNow()

	/** Downloads the available release and hands it to the installer, which quits the app. */
	fun update()

	/** Hides the banner for the available version until a newer one appears. */
	fun dismiss()
}

sealed interface AppUpdateState {
	/** This build cannot update itself; nothing update-related is shown. */
	data object Unsupported : AppUpdateState

	/** No check has completed yet. */
	data object Idle : AppUpdateState

	data object Checking : AppUpdateState

	/** The last check found nothing newer. */
	data object UpToDate : AppUpdateState

	/**
	 * A newer release exists. [installable] is false when this install can only be told about it
	 * and sent to the release page; [dismissed] hides the banner but not the About screen row.
	 */
	data class Available(
		val version: String,
		val installable: Boolean,
		val dismissed: Boolean = false,
	) : AppUpdateState

	data class Downloading(val version: String, val fraction: Float) : AppUpdateState

	/** The installer has been handed the file; the app is about to quit. */
	data class Installing(val version: String) : AppUpdateState

	data class Failed(val version: String, val reason: String) : AppUpdateState
}

object NoOpAppUpdater : AppUpdater {
	override val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.Unsupported)
	override fun checkNow() = Unit
	override fun update() = Unit
	override fun dismiss() = Unit
}
