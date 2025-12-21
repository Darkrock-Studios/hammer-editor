package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projectselection.settings.SpellCheckSettingsUi
import com.darkrockstudios.apps.hammer.project_home_settings_close_button
import com.darkrockstudios.apps.hammer.project_home_settings_title

@Composable
fun ProjectSettingsUi(
	modifier: Modifier,
	component: ProjectSettings,
	onClose: () -> Unit,
) {
	Column(modifier = modifier.padding(horizontal = Ui.Padding.XL)) {
		// Header section
		val screen = LocalScreenCharacteristic.current
		when (screen.windowWidthClass) {
			WindowWidthSizeClass.Companion.Compact -> {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					HeaderUi(
						Res.string.project_home_settings_title,
						"\uD83D\uDEE0",
						Modifier.weight(1f).padding(top = Ui.Padding.L)
					)
					IconButton(onClick = onClose) {
						Icon(
							Icons.Default.Close,
							tint = MaterialTheme.colorScheme.onBackground,
							contentDescription = Res.string.project_home_settings_close_button.get()
						)
					}
				}
			}

			else -> {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						Res.string.project_home_settings_title.get(),
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.displayMedium,
						color = MaterialTheme.colorScheme.onSurface
					)
					IconButton(onClick = onClose) {
						Icon(
							Icons.Default.Close,
							contentDescription = Res.string.project_home_settings_close_button.get(),
							tint = MaterialTheme.colorScheme.onBackground,
						)
					}
				}
			}
		}

		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		SpellCheckSettingsUi(component.spellCheckSettings)
	}
}
