package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/**
 * A small square color swatch keyed to an [EntryType].
 *
 * Used inline next to category labels and as the visual anchor of
 * [HdCategoryChip]. The default 8dp size matches the dashboard mocks;
 * the encyclopedia card top-strip uses 6dp.
 */
@Composable
fun HdCategorySwatch(
	type: EntryType,
	modifier: Modifier = Modifier,
	size: Dp = 8.dp,
) {
	val color = LocalHammerColors.current.colorFor(type)
	HdSwatch(color = color, modifier = modifier, size = size)
}

/**
 * Untyped color swatch — used by character-color rows that don't map to an
 * [EntryType] (e.g. character-bar rows colored by id hash).
 */
@Composable
fun HdSwatch(
	color: Color,
	modifier: Modifier = Modifier,
	size: Dp = 8.dp,
) {
	androidx.compose.foundation.layout.Box(
		modifier = modifier
			.size(size)
			.clip(RoundedCornerShape(2.dp))
			.background(color),
	)
}

/**
 * Swatch + uppercase mono category name in a single row.
 *
 *     ▌ PERSON
 */
@Composable
fun HdCategoryChip(
	type: EntryType,
	modifier: Modifier = Modifier,
	swatchSize: Dp = 8.dp,
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		HdCategorySwatch(type = type, size = swatchSize)
		HdMonoLabel(
			text = type.text,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
