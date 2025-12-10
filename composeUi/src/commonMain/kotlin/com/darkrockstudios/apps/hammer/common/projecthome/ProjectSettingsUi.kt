package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projectselection.settings.SpellCheckSettingsUi
import com.darkrockstudios.apps.hammer.project_home_settings_title

@Composable
fun ProjectSettingsUi(
	modifier: Modifier,
	component: ProjectSettings,
	otherContent: (@Composable () -> Unit)? = null
) {
	Column(modifier = modifier.padding(horizontal = Ui.Padding.XL)) {
		// Header section
		val screen = LocalScreenCharacteristic.current
		when (screen.windowWidthClass) {
			WindowWidthSizeClass.Companion.Compact -> {
				HeaderUi(
					Res.string.project_home_settings_title,
					"\uD83D\uDEE0",
					Modifier.padding(top = Ui.Padding.L)
				)
			}

			else -> {
				Text(
					Res.string.project_home_settings_title.get(),
					style = MaterialTheme.typography.displayMedium,
					color = MaterialTheme.colorScheme.onSurface
				)
			}
		}

		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		SpellCheckSettingsUi(component.spellCheckSettings)
		// Spell check settings card
//		Card(
//			modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.L),
//			elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
//		) {
//			Column(modifier = Modifier.padding(Ui.Padding.L)) {
//				Text(
//					"Spell Check",
//					style = MaterialTheme.typography.headlineSmall,
//					color = MaterialTheme.colorScheme.onSurface
//				)
//				Spacer(modifier = Modifier.size(Ui.Padding.M))
//				Text(
//					"Spell check is currently enabled for this project.",
//					style = MaterialTheme.typography.bodyLarge,
//					color = MaterialTheme.colorScheme.onSurface
//				)
//			}
//		}

		// Other content if provided
		if (otherContent != null) {
			otherContent()
		}
	}
}
