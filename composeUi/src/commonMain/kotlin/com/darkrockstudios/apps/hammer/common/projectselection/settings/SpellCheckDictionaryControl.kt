package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary

/**
 * Spell check dictionary control. Platforms whose spell checker honors a
 * requested locale (desktop, iOS) render a selectable dropdown; Android renders
 * the active language read-only because its system spell checker picks the
 * language itself.
 */
@Composable
internal expect fun SpellCheckDictionaryControl(
	selected: Locale,
	available: List<Locale>,
	enabled: Boolean,
	onSelect: (Locale) -> Unit,
	modifier: Modifier = Modifier,
)

@Composable
internal fun DictionaryDropdown(
	selected: Locale,
	available: List<Locale>,
	enabled: Boolean,
	onSelect: (Locale) -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(modifier = modifier.alpha(if (enabled) 1f else 0.45f)) {
		HdHairlineDropdown(
			title = Res.string.settings_spellcheck_dictionary.get(),
			options = available,
			selected = selected,
			onSelect = { if (enabled) onSelect(it) },
			label = { it.displayName() },
		)
	}
}
