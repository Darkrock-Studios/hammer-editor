package com.darkrockstudios.apps.hammer.wear.data

/**
 * What the tile and complication show without opening the app: where a note would go, and how much
 * writing is still waiting to reach the server. Both are null when they could not be read, so a
 * surface can leave them out rather than state something untrue.
 */
data class CaptureTileState(
	val projectName: String? = null,
	val pending: Int? = null,
)

class CaptureTileStateUseCase(
	private val captureTargets: CaptureTargetsUseCase,
	private val unsyncedContent: UnsyncedContentUseCase,
) {
	suspend fun load(): CaptureTileState = CaptureTileState(
		projectName = captureTargets.load().default?.projectDef?.name,
		pending = unsyncedContent.pending().total,
	)
}
