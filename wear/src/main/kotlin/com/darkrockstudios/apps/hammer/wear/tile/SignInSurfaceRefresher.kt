package com.darkrockstudios.apps.hammer.wear.tile

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.data.isSignedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/** The tile swaps between a sign-in prompt and capture, so it has to hear about sign-in changes. */
class SignInSurfaceRefresher(
	private val globalSettingsStore: GlobalSettingsStore,
	private val surfaces: CaptureSurfaceUpdater,
	private val appScope: CoroutineScope,
) {
	fun start() {
		appScope.launch {
			globalSettingsStore.serverSettingsUpdates
				.map { it.isSignedIn() }
				.onStart { emit(globalSettingsStore.serverSettings.isSignedIn()) }
				.distinctUntilChanged()
				.drop(1)
				.collect { surfaces.refresh() }
		}
	}
}
