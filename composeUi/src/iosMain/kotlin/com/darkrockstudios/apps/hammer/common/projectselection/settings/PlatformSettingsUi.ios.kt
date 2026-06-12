package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_platform_settings_empty

@Composable
actual fun ColumnScope.PlatformSettingsUi(component: PlatformSettings) {
	Text(
		text = Res.string.settings_platform_settings_empty.get(),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}
