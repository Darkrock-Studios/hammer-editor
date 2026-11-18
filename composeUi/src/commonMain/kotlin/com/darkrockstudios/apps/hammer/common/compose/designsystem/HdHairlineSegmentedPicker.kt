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
 * [HdHairlineDropdown] does.
 *
 * [compact] is the settings-row form: cells as wide as the widest label instead of the full
 * row, labels in sentence case body type. Stacked under an [HdSettingRow]'s title it spans the width.
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
	compact: Boolean = false,
) {
	Column(
		modifier = modifier.alpha(if (enabled) 1f else 0.45f),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (title != null) {
			HdMonoLabel(text = title)
		}
		val hugContent = compact && !LocalHdSettingRowStacked.current
		Row(modifier = if (hugContent) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth()) {
			options.forEachIndexed { index, option ->
				HdSegmentedCell(
					label = label(option),
					selected = option == selected,
					onClick = { onSelect(option) },
					enabled = enabled,
					compact = compact,
					modifier = Modifier
						.weight(1f)
						.offset(x = if (index == 0) 0.dp else (-1).dp),
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
	modifier: Modifier = Modifier,
	compact: Boolean = false,
) {
	val borderColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.outlineVariant
	val labelColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 36.dp)
			.hdControlFrame(borderColor, keepBorder = selected)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = if (compact) label else label.uppercase(),
			style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
			color = labelColor,
			maxLines = 1,
			softWrap = false,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
