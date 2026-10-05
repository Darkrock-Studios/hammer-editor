package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_update_available
import com.darkrockstudios.apps.hammer.about_update_checking
import com.darkrockstudios.apps.hammer.about_update_downloading
import com.darkrockstudios.apps.hammer.about_update_failed
import com.darkrockstudios.apps.hammer.about_update_install_button
import com.darkrockstudios.apps.hammer.about_update_installing
import com.darkrockstudios.apps.hammer.about_update_later_button
import com.darkrockstudios.apps.hammer.about_update_quit_notice
import com.darkrockstudios.apps.hammer.about_update_retry_button
import com.darkrockstudios.apps.hammer.about_update_up_to_date
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.update_dialog_open_release_button
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** "42%" for a 0..1 fraction; built here so the string resource needs no escaped percent sign. */
fun percentLabel(fraction: Float): String = "${(fraction * PERCENT).roundToInt()}%"

/**
 * The one-line description of an update state, shared by the project-list banner and the
 * About screen row, or null when there is nothing to say.
 */
@Composable
fun appUpdateDescription(state: AppUpdateState): String? = when (state) {
	AppUpdateState.Checking -> Res.string.about_update_checking.get()
	AppUpdateState.UpToDate -> Res.string.about_update_up_to_date.get()
	is AppUpdateState.Available -> Res.string.about_update_available.get(state.version)
	is AppUpdateState.Downloading ->
		Res.string.about_update_downloading.get(state.version, percentLabel(state.fraction))

	is AppUpdateState.Installing -> Res.string.about_update_installing.get(state.version)
	is AppUpdateState.Failed -> Res.string.about_update_failed.get(state.version, state.reason)
	AppUpdateState.Idle,
	AppUpdateState.Unsupported -> null
}

/**
 * The strip above the project list that offers a newer release. Nothing is drawn until a check
 * has found one, and nothing ever is on a build that cannot update itself.
 */
@Composable
fun AppUpdateBanner(
	state: AppUpdateState,
	onUpdate: () -> Unit,
	onOpenRelease: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val title = appUpdateDescription(state) ?: return
	when (state) {
		is AppUpdateState.Available -> if (!state.dismissed) {
			HdNoticeStrip(
				title = title,
				detail = if (state.installable) Res.string.about_update_quit_notice.get() else null,
				primaryLabel = if (state.installable) {
					Res.string.about_update_install_button.get(state.version)
				} else {
					Res.string.update_dialog_open_release_button.get()
				},
				onPrimary = if (state.installable) onUpdate else onOpenRelease,
				secondaryLabel = Res.string.about_update_later_button.get(),
				onSecondary = onDismiss,
				modifier = modifier,
			)
		}

		is AppUpdateState.Downloading -> HdNoticeStrip(
			title = title,
			progress = state.fraction,
			modifier = modifier,
		)

		is AppUpdateState.Installing -> HdNoticeStrip(
			title = title,
			modifier = modifier,
		)

		is AppUpdateState.Failed -> HdNoticeStrip(
			title = title,
			primaryLabel = Res.string.about_update_retry_button.get(),
			onPrimary = onUpdate,
			secondaryLabel = Res.string.update_dialog_open_release_button.get(),
			onSecondary = onOpenRelease,
			modifier = modifier,
		)

		// Described, but only the About row says so.
		AppUpdateState.Checking,
		AppUpdateState.UpToDate,
		AppUpdateState.Idle,
		AppUpdateState.Unsupported -> Unit
	}
}
