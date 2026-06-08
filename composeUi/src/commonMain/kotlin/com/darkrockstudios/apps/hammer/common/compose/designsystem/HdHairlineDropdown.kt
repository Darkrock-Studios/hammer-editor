package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Generic hairline dropdown — a full-width square-cornered pill showing the
 * selected label and a `▾` chevron that opens a [DropdownMenu] of [options].
 *
 *     ┌─────────────────────────────────┐
 *     │ Markdown                      ▾ │
 *     └─────────────────────────────────┘
 *
 * The resource-label sibling of [HdHairlineSegmentedPicker] — same signature, so
 * swapping between them is one-for-one. Use for enums of 4+ values where a
 * segmented row would crowd; the optional [title] renders as an [HdMonoLabel]
 * above the pill with 8dp spacing.
 */
@Composable
fun <T> HdHairlineDropdown(
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
	label: @Composable (T) -> String,
	modifier: Modifier = Modifier,
	title: String? = null,
) {
	var expanded by remember { mutableStateOf(false) }
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (title != null) {
			HdMonoLabel(text = title)
		}
		Box {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 36.dp)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.clickable { expanded = true }
					.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				Text(
					text = label(selected),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					softWrap = false,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				HdMonoLabel(
					text = "▾",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			DropdownMenu(
				expanded = expanded,
				onDismissRequest = { expanded = false },
			) {
				options.forEach { option ->
					DropdownMenuItem(
						text = {
							Text(
								text = label(option),
								style = MaterialTheme.typography.bodyMedium,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								color = if (option == selected) {
									MaterialTheme.colorScheme.onSurface
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
							)
						},
						onClick = {
							onSelect(option)
							expanded = false
						},
					)
				}
			}
		}
	}
}
