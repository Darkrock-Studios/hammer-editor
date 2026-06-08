package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.BuildConfig
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AndroidPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import dev.icerock.moko.permissions.compose.BindEffect

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as AndroidPlatformSettingsComponent
	val state by component.state.subscribeAsState()

	BindEffect(component.permissionsController)

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		HdHairlineToggleRow(
			checked = state.keepScreenOn,
			onCheckedChange = component::updateKeepScreenOn,
			label = Res.string.settings_keep_screen_on.get(),
		)
		Column(
			modifier = Modifier.alpha(if (state.dndPermissionGranted) 1f else 0.45f),
		) {
			HdHairlineToggleRow(
				checked = state.enableDndInFocusMode,
				onCheckedChange = {
					if (state.dndPermissionGranted) {
						component.updateEnableDndInFocusMode(it)
					}
				},
				label = Res.string.settings_enable_dnd_focus_mode_title.get(),
			)
		}
		if (!state.dndPermissionGranted) {
			val activity = LocalActivity.current
			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					text = Res.string.settings_dnd_permission_explanation.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
				)
				HdHairlineButton(
					label = Res.string.settings_dnd_permission_button.get(),
					onClick = {
						activity?.let {
							component.launchNotificationPolicyPermissionScreen(activity)
						}
					},
					emphasised = true,
				)
			}
		}

		// Public-storage projects are only offered on F-Droid builds: the required
		// MANAGE_EXTERNAL_STORAGE permission is not permitted on Google Play.
		if (BuildConfig.FDROID) {
			PublicStorageSettings(component, state)
		}
	}
}

@Composable
private fun PublicStorageSettings(
	component: AndroidPlatformSettingsComponent,
	state: AndroidPlatformSettingsComponent.PlatformState,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		HdHairlineToggleRow(
			checked = !state.dataStorageInternal,
			onCheckedChange = { usePublic ->
				if (usePublic) {
					if (state.fileAccessGranted) {
						component.setExternalStorage()
					} else {
						component.promptForFileAccess()
					}
				} else {
					component.setInternalStorage()
				}
			},
			label = Res.string.settings_storage_public_label.get(),
			hint = Res.string.settings_storage_public_hint.get(),
		)

		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMonoLabel(text = Res.string.settings_storage_location_label.get())
			Text(
				text = state.projectsDir.path,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}

		if (!state.fileAccessGranted) {
			Text(
				text = Res.string.settings_storage_permission_explanation.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
			HdHairlineButton(
				label = Res.string.settings_storage_permission_button.get(),
				onClick = component::promptForFileAccess,
				emphasised = true,
			)
		}
	}
}
