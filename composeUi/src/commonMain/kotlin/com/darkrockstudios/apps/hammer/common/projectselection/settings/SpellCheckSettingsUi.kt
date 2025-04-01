package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.SpacerM
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.spellcheck.Language
import kotlinx.coroutines.launch

@Composable
internal fun SpellCheckSettingsUi(
	component: SpellCheckSettings,
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			MR.strings.settings_spellcheck_heading.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)

		Text(
			MR.strings.settings_spellcheck_notice.get(),
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.onBackground,
		)

		SpacerM()

		var spellCheckingEnabledValue by remember { mutableStateOf(state.spellCheckingEnabled) }
		Row {
			Checkbox(
				checked = spellCheckingEnabledValue,
				onCheckedChange = {
					scope.launch { component.setSpellcheckEnable(it) }
					spellCheckingEnabledValue = it
				}
			)
			Text(
				MR.strings.settings_spellcheck_enable.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
				modifier = Modifier.align(Alignment.CenterVertically)
			)
		}

		Row {
			var spellCheckingInFocusEnabledValue by remember { mutableStateOf(state.spellCheckingInFocusEnabled) }
			Checkbox(
				checked = spellCheckingInFocusEnabledValue,
				enabled = spellCheckingEnabledValue,
				onCheckedChange = {
					scope.launch { component.setSpellCheckingInFocusEnabled(it) }
					spellCheckingInFocusEnabledValue = it
				}
			)
			Text(
				MR.strings.settings_spellcheck_in_focus_enable.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
				modifier = Modifier.align(Alignment.CenterVertically)
			)
		}

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		val languageOptions = remember { Language.entries }
		ExposedDropDown(
			modifier = Modifier.defaultMinSize(minWidth = 256.dp),
			label = MR.strings.settings_spellcheck_dictionary.get(),
			items = languageOptions,
			selectedItem = state.spellCheckingLanguage,
			enabled = spellCheckingEnabledValue,
		) { selectedTheme ->
			if (selectedTheme != null) {
				scope.launch { component.setSpellCheckLanguage(selectedTheme) }
			}
		}
	}
}
