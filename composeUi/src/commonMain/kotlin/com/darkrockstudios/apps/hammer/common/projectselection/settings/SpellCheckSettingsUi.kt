package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.spellcheck.Language
import kotlinx.coroutines.launch

@Composable
internal fun SpellCheckSettingsUi(
	component: AccountSettings,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			MR.strings.settings_spellcheck_heading.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)

		Row {
			var spellCheckingEnabledValue by remember { mutableStateOf(state.spellCheckingEnabled) }
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

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		Row {
			var spellCheckingInFocusEnabledValue by remember { mutableStateOf(state.spellCheckingInFocusEnabled) }
			Checkbox(
				checked = spellCheckingInFocusEnabledValue,
				enabled = state.spellCheckingEnabled,
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
		) { selectedTheme ->
			if (selectedTheme != null) {
				scope.launch { component.setSpellCheckLanguage(selectedTheme) }
			}
		}
	}
}