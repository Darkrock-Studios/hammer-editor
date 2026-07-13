package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import org.jetbrains.compose.resources.StringResource
import com.darkrockstudios.apps.hammer.common.compose.resources.get

interface HdSortOption {
	/** Long-form label shown in the dropdown menu (e.g. "Newest"). */
	val labelRes: StringResource

	/** Short glyph shown on the trigger button and next to selected items (e.g. "↓ DATE"). */
	val glyphRes: StringResource
}

/**
 * Hairline-bordered sort affordance: a "SORT  ↓ DATE" pill that opens a
 * dropdown of [options]. Each option's [HdSortOption.labelRes] appears as the
 * menu text, with its [HdSortOption.glyphRes] echoed on the trailing edge.
 */
@Composable
fun <T : HdSortOption> HdSortMenu(
	label: StringResource,
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
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
				text = label.get(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				softWrap = false,
			)
			HdMonoLabel(
				text = selected.glyphRes.get(),
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				softWrap = false,
			)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			options.forEach { option ->
				DropdownMenuItem(
					text = {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = option.labelRes.get(),
								style = MaterialTheme.typography.bodyMedium,
								color = if (option == selected) {
									MaterialTheme.colorScheme.onSurface
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
							)
							Spacer(modifier = Modifier.weight(1f))
							Spacer(modifier = Modifier.width(Ui.Padding.XL))
							HdMonoLabel(text = option.glyphRes.get())
						}
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
