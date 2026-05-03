package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Label-over-value pair stacked vertically (mono caps label, value in
 * titleMedium). For the inline label-left/value-right pattern, use
 * [HdInlineStat].
 *
 * Pass [selectable] = true to wrap the value in a `SelectionContainer`
 * — useful for paths or IDs the user may want to copy.
 */
@Composable
fun HdMetadataItem(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
	selectable: Boolean = false,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(
			text = label,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		val valueText = @Composable {
			Text(
				text = value,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		if (selectable) {
			SelectionContainer { valueText() }
		} else {
			valueText()
		}
	}
}
