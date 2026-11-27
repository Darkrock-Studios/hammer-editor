package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore

/**
 * What the tile and complication show without opening the app: whether there is an account to
 * capture into, where a note would go, and how much writing is still waiting to reach the server.
 * The last two are null when signed out or when they could not be read, so a surface can leave them
 * out rather than state something untrue.
 */
data class CaptureTileState(
	val signedIn: Boolean = true,
	val projectName: String? = null,
	val pending: Int? = null,
)

class CaptureTileStateUseCase(
	private val captureTargets: CaptureTargetsUseCase,
	private val unsyncedContent: UnsyncedContentUseCase,
	private val globalSettingsStore: GlobalSettingsStore,
) {
	suspend fun load(): CaptureTileState {
		if (!globalSettingsStore.serverSettings.isSignedIn()) return CaptureTileState(signedIn = false)
		return CaptureTileState(
			projectName = captureTargets.load().default?.projectDef?.name,
			pending = unsyncedContent.pending().total,
		)
	}
}
