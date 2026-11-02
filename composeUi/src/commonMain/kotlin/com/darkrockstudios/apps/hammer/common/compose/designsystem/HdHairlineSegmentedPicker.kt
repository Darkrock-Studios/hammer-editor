package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Segmented hairline picker — `[ DAY │ WEEK ]`. Each cell is a
 * square-cornered hairline-bordered box; cells share borders by
 * overlapping by 1dp. The selected cell takes the `onSurface` border
 * and label color, the rest stay muted.
 *
 * The optional [title] renders as an [HdMonoLabel] above the row, with
 * 8dp spacing. A disabled picker dims and ignores clicks, as
 * [HdHairlineDropdown] does; [isEnabled] does the same for one cell.
 * [trailing] adds a small affordance after a cell's label, such as an
 * [HdClearGlyph] that dismisses that option:
 *
 *     [ EVERYTHING │ STORY │ THE STORM  × ]
 *
 * [cellModifier] reaches one cell, for a test tag or a description.
 */
@Composable
fun <T> HdHairlineSegmentedPicker(
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
	label: @Composable (T) -> String,
	modifier: Modifier = Modifier,
	title: String? = null,
	enabled: Boolean = true,
	isEnabled: (T) -> Boolean = { true },
	trailing: (T) -> (@Composable () -> Unit)? = { null },
	cellModifier: @Composable (T) -> Modifier = { Modifier },
) {
	Column(
		modifier = modifier.alpha(if (enabled) 1f else 0.45f),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (title != null) {
			HdMonoLabel(text = title)
		}
		Row(modifier = Modifier.fillMaxWidth()) {
			options.forEachIndexed { index, option ->
				val cellEnabled = isEnabled(option)
				HdSegmentedCell(
					label = label(option),
					selected = option == selected,
					onClick = { onSelect(option) },
					enabled = enabled && cellEnabled,
					trailing = trailing(option),
					modifier = Modifier
						.weight(1f)
						.offset(x = if (index == 0) 0.dp else (-1).dp)
						.alpha(if (cellEnabled) 1f else 0.45f)
						.then(cellModifier(option)),
				)
			}
		}
	}
}

@Composable
private fun HdSegmentedCell(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	enabled: Boolean,
	trailing: (@Composable () -> Unit)?,
	modifier: Modifier = Modifier,
) {
	val borderColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.outlineVariant
	val labelColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 36.dp)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = label.uppercase(),
				style = MaterialTheme.typography.labelMedium,
				color = labelColor,
				maxLines = 1,
				softWrap = false,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)
			if (trailing != null) {
				Spacer(Modifier.width(4.dp))
				trailing()
			}
		}
	}
}
