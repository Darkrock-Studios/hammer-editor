package com.darkrockstudios.apps.hammer.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.compactButton
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.darkrockstudios.apps.hammer.wear.CaptureActivity
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture
import com.darkrockstudios.apps.hammer.wear.data.CaptureTileState
import com.darkrockstudios.apps.hammer.wear.data.CaptureTileStateUseCase
import com.google.common.util.concurrent.ListenableFuture
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** One tap from the watch face to dictating a note or an idea. */
class CaptureTileService : TileService(), KoinComponent {

	private val tileState: CaptureTileStateUseCase by inject()
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onDestroy() {
		serviceScope.cancel("Tile service destroyed")
		super.onDestroy()
	}

	override fun onTileRequest(
		requestParams: RequestBuilders.TileRequest,
	): ListenableFuture<TileBuilders.Tile> = future { completer ->
		serviceScope.launch {
			val state = try {
				tileState.load()
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e("Failed to read the capture tile's state", e)
				CaptureTileState()
			}
			completer.set(buildTile(state, requestParams.deviceConfiguration))
		}
	}

	override fun onTileResourcesRequest(
		requestParams: RequestBuilders.ResourcesRequest,
	): ListenableFuture<ResourceBuilders.Resources> = future { completer ->
		completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
	}

	private fun buildTile(state: CaptureTileState, device: DeviceParameters): TileBuilders.Tile =
		TileBuilders.Tile.Builder()
			.setResourcesVersion(RESOURCES_VERSION)
			.setTileTimeline(
				TimelineBuilders.Timeline.fromLayoutElement(layout(state, device))
			)
			// Nothing tells the tile a sync finished, so it re-reads on its own cadence.
			.setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(FRESHNESS_MINUTES))
			.build()

	private fun layout(state: CaptureTileState, device: DeviceParameters): LayoutElement =
		materialScope(this, device) {
			primaryLayout(
				// The project sits in the main slot, not the title: the title slot does not
				// receive taps, and this one has to be pressable to change where a note goes.
				mainSlot = {
					LayoutElementBuilders.Column.Builder()
						.setWidth(expand())
						.setHeight(expand())
						.addContent(projectButton(state))
						.addContent(
							LayoutElementBuilders.Spacer.Builder()
								.setHeight(dp(PROJECT_TO_BUTTONS_SPACING_DP))
								.build()
						)
						.addContent(
							buttonGroup {
								buttonGroupItem { captureButton(Capture.Mode.Note, R.string.tile_note) }
								buttonGroupItem { captureButton(Capture.Mode.Idea, R.string.tile_idea) }
							}
						)
						.build()
				},
				bottomSlot = state.pending?.takeIf { it > 0 }?.let { pending ->
					{ pendingText(pending) }
				},
			)
		}

	/** A button, not a label: this is how the project a note goes to gets changed. */
	private fun MaterialScope.projectButton(state: CaptureTileState): LayoutElement = compactButton(
		onClick = clickable(
			action = launchCapture(Capture.Mode.Note, pickProject = true),
			id = "pick_project",
		),
		labelContent = {
			text(
				LayoutString(state.projectName ?: getString(R.string.tile_no_project)),
				maxLines = 1,
			)
		},
		colors = filledTonalButtonColors(),
	)

	private fun MaterialScope.pendingText(pending: Int): LayoutElement = text(
		text = LayoutString(getString(R.string.tile_pending, pending)),
		typography = Typography.BODY_MEDIUM,
		maxLines = 1,
	)

	private fun MaterialScope.captureButton(mode: Capture.Mode, labelRes: Int): LayoutElement =
		textButton(
			onClick = clickable(action = launchCapture(mode), id = mode.name),
			// Two lines so "Story idea" reads in full rather than truncating.
			labelContent = { text(LayoutString(getString(labelRes)), maxLines = 2) },
			width = expand(),
			height = expand(),
		)

	private fun launchCapture(
		mode: Capture.Mode,
		pickProject: Boolean = false,
	): ActionBuilders.LaunchAction = ActionBuilders.LaunchAction.Builder()
		.setAndroidActivity(
			ActionBuilders.AndroidActivity.Builder()
				.setPackageName(packageName)
				.setClassName(CaptureActivity::class.java.name)
				.addKeyToExtraMapping(CaptureActivity.EXTRA_MODE, ActionBuilders.stringExtra(mode.name))
				// Thar be dragons: the tile renderer drops an AndroidBooleanExtra, and the whole
				// launch action goes with it, so this travels as a string.
				.addKeyToExtraMapping(
					CaptureActivity.EXTRA_PICK_PROJECT,
					ActionBuilders.stringExtra(pickProject.toString()),
				)
				.build()
		)
		.build()

	private companion object {
		const val RESOURCES_VERSION = "1"
		const val FRESHNESS_MINUTES = 15L
		const val PROJECT_TO_BUTTONS_SPACING_DP = 4f
	}
}

private fun <T> future(
	block: (CallbackToFutureAdapter.Completer<T>) -> Unit,
): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { completer ->
	block(completer)
	"CaptureTile"
}
