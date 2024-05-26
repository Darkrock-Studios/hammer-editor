package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AndroidPlatformSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	component as AndroidPlatformSettings
	val state by component.state.subscribeAsState()

	Text(
		MR.strings.settings_platform_settings_title.get(),
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.onBackground,
	)
	Row {
		Checkbox(checked = state.keepScreenOn, onCheckedChange = component::updateKeepScreenOn)
		Text(
			MR.strings.settings_keep_screen_on.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground,
			modifier = Modifier.align(Alignment.CenterVertically),
		)
	}
}