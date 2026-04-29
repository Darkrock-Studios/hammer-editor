package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A `LABEL  value` pair stacked vertically — the building block of the
 * editor scene-meta panel and the dashboard's "TODAY 847 / Daily avg 590"
 * style readouts.
 *
 *     STATUS
 *     Draft
 *
 * For the wider "label on left, value on right" inline pattern (mock's
 * "Today  ·  847" rows under the This Week block), use [HdMetadataRow].
 */
@Composable
fun HdMetadataItem(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(
			text = label,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

/**
 * A `Label                       value` row with the label left-aligned in
 * mono small-caps and the value right-aligned in body weight, separated by
 * a flexible spacer. Used in the `Today / Daily avg / Days written` mini
 * lists in the dashboard.
 */
@Composable
fun HdMetadataRow(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}
