package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * Segmented hairline type picker — one cell per [EntryType] in the
 * order [order]. Each cell stacks a 28dp glyph square (filled with the
 * type's color when active, hairline when not) over a small mono
 * label. The active cell gains a 2dp top stripe in the type color and
 * a `surfaceContainerHigh` fill so it reads as the selected segment.
 *
 *   ┌─────────┬─────────┬─────────┬─────────┐
 *   │ ┌───┐   │ ┌───┐   │ ┌───┐   │ ┌───┐   │
 *   │ │ ☉ │   │ │ ◇ │   │ │ ✦ │   │ │ ⚑ │   │
 *   │ └───┘   │ └───┘   │ └───┘   │ └───┘   │
 *   │ PERSON  │ PLACE   │ THING   │ EVENT   │
 *   └─────────┴─────────┴─────────┴─────────┘
 */
@Composable
fun HdHairlineTypePicker(
	selected: EntryType,
	onSelect: (EntryType) -> Unit,
	modifier: Modifier = Modifier,
	order: List<EntryType> = listOf(
		EntryType.PERSON,
		EntryType.PLACE,
		EntryType.THING,
		EntryType.EVENT,
		EntryType.IDEA,
	),
	cellTestTag: ((EntryType) -> String)? = null,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(74.dp)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape),
	) {
		order.forEachIndexed { index, type ->
			if (index > 0) {
				Box(
					modifier = Modifier
						.width(Dp.Hairline)
						.fillMaxHeight()
						.background(ruleColor),
				)
			}
			HdTypePickerCell(
				type = type,
				active = type == selected,
				onClick = { onSelect(type) },
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight(),
				testTag = cellTestTag?.invoke(type),
			)
		}
	}
}

@Composable
private fun HdTypePickerCell(
	type: EntryType,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	testTag: String? = null,
) {
	val typeColor = LocalHammerColors.current.colorFor(type)
	val outlineVariant = MaterialTheme.colorScheme.outlineVariant
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	val stripeColor = if (active) typeColor else Color.Transparent

	Column(
		modifier = modifier
			.background(background)
			.clickable(onClick = onClick)
			.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(2.dp)
				.background(stripeColor),
		)
		Column(
			modifier = Modifier
				.padding(top = 8.dp, bottom = 8.dp)
				.fillMaxWidth(),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Box(
				modifier = Modifier.size(28.dp).then(
					if (active) Modifier.background(typeColor)
					else Modifier.border(
						width = Dp.Hairline,
						color = outlineVariant,
						shape = RectangleShape,
					),
				),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = type.glyph(),
					style = MaterialTheme.typography.titleMedium,
					color = if (active) Color.Black else typeColor,
					fontWeight = FontWeight.Medium,
				)
			}
			HdMonoLabel(
				text = type.toStringResource().get(),
				color = labelColor,
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
			)
		}
	}
}
