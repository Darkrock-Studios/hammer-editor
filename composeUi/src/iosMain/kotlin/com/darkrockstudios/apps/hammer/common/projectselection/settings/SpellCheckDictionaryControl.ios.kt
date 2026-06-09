package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fluidsonic.locale.Locale

@Composable
internal actual fun SpellCheckDictionaryControl(
	selected: Locale,
	available: List<Locale>,
	enabled: Boolean,
	onSelect: (Locale) -> Unit,
	modifier: Modifier,
) {
	DictionaryDropdown(
		selected = selected,
		available = available,
		enabled = enabled,
		onSelect = onSelect,
		modifier = modifier,
	)
}
