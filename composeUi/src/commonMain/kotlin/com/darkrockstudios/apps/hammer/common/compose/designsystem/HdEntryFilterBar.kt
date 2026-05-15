package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * One option in [HdEntryFilterBar]. Pass [type] = `null` for the "all"
 * position; that uses the [HD_ALL_GLYPH] sigil and an outlined glyph
 * cell instead of a colored fill.
 */
data class HdEntryFilterOption(
	val type: EntryType?,
	val label: String,
	val count: Int,
)

/**
 * Segmented hairline-bordered filter bar — All / People / Places / Things
 * etc. Each cell shows a colored glyph square + uppercase mono label
 * with a count. Clicking the cell flips the filter to that type.
 *
 *   ┌─────────┬──────────────┬──────────────┐
 *   │ ∗ ALL   │ ☉ PEOPLE · 12│ ◇ PLACES · 18│ …
 *   └─────────┴──────────────┴──────────────┘
 */
@Composable
fun HdEntryFilterBar(
	options: List<HdEntryFilterOption>,
	selected: EntryType?,
	onSelect: (EntryType?) -> Unit,
	modifier: Modifier = Modifier,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	Row(
		modifier = modifier
			.height(32.dp)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		options.forEachIndexed { index, option ->
			if (index > 0) {
				Box(
					modifier = Modifier
						.width(Dp.Hairline)
						.fillMaxHeight()
						.background(ruleColor),
				)
			}
			HdEntryFilterCell(
				option = option,
				selected = option.type == selected,
				onClick = { onSelect(option.type) },
				modifier = Modifier.fillMaxHeight(),
			)
		}
	}
}

@Composable
private fun HdEntryFilterCell(
	option: HdEntryFilterOption,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val background = if (selected) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (selected) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	val type = option.type
	val glyph = type?.glyph() ?: HD_ALL_GLYPH
	val glyphColor = type?.let { LocalHammerColors.current.colorFor(it) }

	Row(
		modifier = modifier
			.background(background)
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.size(16.dp)
				.then(
					if (glyphColor != null) {
						Modifier.background(glyphColor)
					} else {
						Modifier.border(
							width = Dp.Hairline,
							color = MaterialTheme.colorScheme.outlineVariant,
							shape = RectangleShape,
						)
					},
				),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = glyph,
				style = MaterialTheme.typography.labelSmall,
				color = if (glyphColor != null) {
					Color.Black
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				fontWeight = FontWeight.Medium,
			)
		}
		HdMonoLabel(
			text = "${option.label} · ${option.count}",
			color = labelColor,
		)
	}
}
