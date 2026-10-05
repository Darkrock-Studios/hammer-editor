package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_update_check_button
import com.darkrockstudios.apps.hammer.about_update_install_button
import com.darkrockstudios.apps.hammer.about_update_retry_button
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineProgressBar
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.projectselection.appUpdateDescription

/**
 * The update row on the About screen's version card. Hidden on builds that cannot update, so
 * store builds show no trace of it. A release this install cannot install itself is only
 * announced; the card's existing release-page button covers the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateStatus(
	state: AppUpdateState,
	onCheckForUpdate: () -> Unit,
	onUpdateApp: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (state is AppUpdateState.Unsupported) return

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		appUpdateDescription(state)?.let { text ->
			Text(
				text = text,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		if (state is AppUpdateState.Downloading) {
			HdHairlineProgressBar(progress = state.fraction)
		}
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			when (state) {
				is AppUpdateState.Available -> {
					if (state.installable) {
						HdHairlineButton(
							label = Res.string.about_update_install_button.get(state.version),
							onClick = onUpdateApp,
							emphasised = true,
						)
					}
					CheckButton(onCheckForUpdate)
				}

				is AppUpdateState.Failed -> {
					HdHairlineButton(
						label = Res.string.about_update_retry_button.get(),
						onClick = onUpdateApp,
						emphasised = true,
					)
					CheckButton(onCheckForUpdate)
				}

				AppUpdateState.Checking -> CheckButton(onCheckForUpdate, enabled = false)

				AppUpdateState.Idle,
				AppUpdateState.UpToDate -> CheckButton(onCheckForUpdate)

				is AppUpdateState.Downloading,
				is AppUpdateState.Installing,
				AppUpdateState.Unsupported -> Unit
			}
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
