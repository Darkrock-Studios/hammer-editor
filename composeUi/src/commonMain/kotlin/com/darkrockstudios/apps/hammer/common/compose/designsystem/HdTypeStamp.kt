package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * Postage-stamp filter affordance. Two-pane: a colored glyph square
 * on the left, a stacked `TYPE / FILTER ↗` label on the right. Sits
 * top-left of an entry card; clicking re-runs the encyclopedia filter.
 *
 *   ┌────┬───────────┐
 *   │ ☉  │  PERSON   │
 *   │    │  FILTER ↗ │
 *   └────┴───────────┘
 */
@Composable
fun HdTypeStamp(
	type: EntryType,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	height: Dp = 44.dp,
	actionLabel: String = "FILTER ↗",
) {
	val color = LocalHammerColors.current.colorFor(type)
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

	Row(
		modifier = modifier
			.height(height)
			.background(surfaceColor, RectangleShape)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(height)
				.fillMaxHeight()
				.background(color),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = type.glyph(),
				style = MaterialTheme.typography.titleMedium,
				color = Color.Black,
				fontWeight = FontWeight.Medium,
			)
		}
		Box(
			modifier = Modifier
				.width(Dp.Hairline)
				.fillMaxHeight()
				.background(ruleColor),
		)
		Column(
			modifier = Modifier.padding(horizontal = 12.dp),
			verticalArrangement = Arrangement.Center,
		) {
			HdMonoLabel(
				text = label,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.labelSmall,
			)
			Text(
				text = actionLabel,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurface,
				fontWeight = FontWeight.Medium,
			)
		}
	}
}
