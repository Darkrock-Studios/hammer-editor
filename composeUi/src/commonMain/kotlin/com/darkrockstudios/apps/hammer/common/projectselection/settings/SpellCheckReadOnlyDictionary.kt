package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary_android_hint

/** The dictionary as Android shows it: the device's language, read-only, since the system picks it. */
@Composable
internal fun ReadOnlyDictionaryControl(
	selected: Locale,
	enabled: Boolean,
	modifier: Modifier = Modifier,
) {
	HdSettingRow(
		title = Res.string.settings_spellcheck_dictionary.get(),
		description = Res.string.settings_spellcheck_dictionary_android_hint.get(),
		modifier = modifier.alpha(if (enabled) 1f else 0.45f),
	) {
		Text(
			text = selected.displayName(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
