package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.close_dialog_button
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary_android_info_message
import com.darkrockstudios.apps.hammer.settings_spellcheck_dictionary_android_info_title

@Composable
internal actual fun SpellCheckDictionaryControl(
	selected: Locale,
	available: List<Locale>,
	enabled: Boolean,
	onSelect: (Locale) -> Unit,
	modifier: Modifier,
) {
	var showInfo by remember { mutableStateOf(false) }

	Column(
		modifier = modifier.alpha(if (enabled) 1f else 0.45f),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdMonoLabel(text = Res.string.settings_spellcheck_dictionary.get())
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 36.dp)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.clickable { showInfo = true }
				.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		) {
			Text(
				text = selected.displayName(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
			Text(
				text = "ⓘ",
				style = MaterialTheme.typography.titleLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}

	if (showInfo) {
		AlertDialog(
			onDismissRequest = { showInfo = false },
			shape = RectangleShape,
			title = { Text(text = Res.string.settings_spellcheck_dictionary_android_info_title.get()) },
			text = { Text(text = Res.string.settings_spellcheck_dictionary_android_info_message.get()) },
			confirmButton = {
				TextButton(onClick = { showInfo = false }) {
					Text(text = Res.string.close_dialog_button.get())
				}
			},
		)
	}
}
