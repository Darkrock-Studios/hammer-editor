package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

private val SwatchShape = RoundedCornerShape(2.dp)

/**
 * Square color swatch keyed to an [EntryType] (place=green, person=red,
 * etc.) — pulled from `LocalHammerColors` so colors stay stable across
 * projects regardless of the per-project theme override.
 */
@Composable
fun HdCategorySwatch(
	type: EntryType,
	modifier: Modifier = Modifier,
	size: Dp = 8.dp,
) {
	val color = LocalHammerColors.current.colorFor(type)
	Box(
		modifier = modifier
			.size(size)
			.clip(SwatchShape)
			.background(color),
	)
}

/** Swatch + uppercase mono category name (`▌ PERSON`). */
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
