package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_spellcheck_enable
import com.darkrockstudios.apps.hammer.settings_spellcheck_encyclopedia_enable
import com.darkrockstudios.apps.hammer.settings_spellcheck_encyclopedia_enable_hint
import com.darkrockstudios.apps.hammer.settings_spellcheck_in_focus_enable
import kotlinx.coroutines.launch

@Composable
internal fun SpellCheckSettingsContent(
	component: SpellCheckSettings,
	showEncyclopediaToggle: Boolean = true,
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		HdHairlineToggleRow(
			checked = state.spellCheckingEnabled,
			label = Res.string.settings_spellcheck_enable.get(),
			onCheckedChange = { scope.launch { component.setSpellcheckEnable(it) } },
		)
		HdHairlineToggleRow(
			checked = state.spellCheckingInFocusEnabled,
			label = Res.string.settings_spellcheck_in_focus_enable.get(),
			enabled = state.spellCheckingEnabled,
			onCheckedChange = { scope.launch { component.setSpellCheckingInFocusEnabled(it) } },
		)
		if (showEncyclopediaToggle) {
			HdHairlineToggleRow(
				checked = state.spellCheckingEncyclopediaEnabled,
				label = Res.string.settings_spellcheck_encyclopedia_enable.get(),
				hint = Res.string.settings_spellcheck_encyclopedia_enable_hint.get(),
				enabled = state.spellCheckingEnabled,
				onCheckedChange = { scope.launch { component.setSpellCheckEncyclopediaEnabled(it) } },
			)
		}
		SpellCheckDictionaryControl(
			selected = state.spellCheckingLanguage,
			available = state.spellCheckLanguages,
			enabled = state.spellCheckingEnabled,
			onSelect = { scope.launch { component.setSpellCheckLanguage(it) } },
		)
	}
}
