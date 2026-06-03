package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Hairline-bordered filter affordance: a "PROJECT  ALICE IN WONDERLAND" pill that
 * opens a dropdown of plain-string [options]. The string sibling of [HdSortMenu] for
 * dynamic, non-resource labels (project names, locales, anything runtime-derived).
 *
 *     ┌─────────────────────────────────┐
 *     │ PROJECT   ALL                 ▾ │
 *     └─────────────────────────────────┘
 */
@Composable
fun HdFilterMenu(
	label: String,
	options: List<String>,
	selected: String,
	onSelect: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier = modifier) {
		Row(
			modifier = Modifier
				.height(32.dp)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.clickable { expanded = true }
				.padding(horizontal = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		) {
			HdMonoLabel(
				text = label,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			HdMonoLabel(
				text = selected,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.widthIn(max = 180.dp),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
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
							text = option,
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
