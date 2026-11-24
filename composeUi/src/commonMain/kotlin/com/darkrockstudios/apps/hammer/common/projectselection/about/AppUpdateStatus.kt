package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_update_check_button
import com.darkrockstudios.apps.hammer.about_update_install_button
import com.darkrockstudios.apps.hammer.about_update_not_checked
import com.darkrockstudios.apps.hammer.about_update_retry_button
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineProgressBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.projectselection.appUpdateDescription

/** Whether [AppUpdateStatus] shows an emphasised install or retry button for this state. */
internal val AppUpdateState.hasPrimaryAction: Boolean
	get() = this is AppUpdateState.Failed || (this is AppUpdateState.Available && installable)

/**
 * Where this install stands on updates, as one setting row with the action that state needs.
 * Hidden on builds that cannot update, so store builds show no trace of it. A release this
 * install cannot install itself is only announced; the release link below it covers the rest.
 */
@Composable
fun AppUpdateStatus(
	state: AppUpdateState,
	onCheckForUpdate: () -> Unit,
	onUpdateApp: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (state is AppUpdateState.Unsupported) return

	Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
		HdSettingRow(title = appUpdateDescription(state) ?: Res.string.about_update_not_checked.get()) {
			when (state) {
				is AppUpdateState.Available ->
					if (state.installable) {
						HdHairlineButton(
							label = Res.string.about_update_install_button.get(),
							onClick = onUpdateApp,
							emphasised = true,
						)
					} else {
						CheckButton(onCheckForUpdate)
					}

				is AppUpdateState.Failed -> HdHairlineButton(
					label = Res.string.about_update_retry_button.get(),
					onClick = onUpdateApp,
					emphasised = true,
				)

				AppUpdateState.Checking -> CheckButton(onCheckForUpdate, enabled = false)

				AppUpdateState.Idle,
				AppUpdateState.UpToDate -> CheckButton(onCheckForUpdate)

				is AppUpdateState.Downloading,
				is AppUpdateState.Installing,
				AppUpdateState.Unsupported -> Unit
			}
		}
		if (state is AppUpdateState.Downloading) {
			HdHairlineProgressBar(progress = state.fraction)
		}
	}
}

@Composable
private fun CheckButton(onClick: () -> Unit, enabled: Boolean = true) {
	HdHairlineButton(
		label = Res.string.about_update_check_button.get(),
		onClick = onClick,
		enabled = enabled,
	)
}
