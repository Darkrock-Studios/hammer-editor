package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.SpacerM
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

/** One chip of a [HdReferenceChipBucket]; [key] is the caller's id for the thing it names. */
data class HdReferenceChipModel(
	val key: Any,
	val glyph: String,
	val swatchColor: Color,
	val glyphColor: Color,
	val name: String,
)

/** The chip model for an encyclopedia entry: its type glyph on its type colour. */
@Composable
fun hdEntryChipModel(key: Any, type: EntryType, name: String): HdReferenceChipModel = HdReferenceChipModel(
	key = key,
	glyph = type.glyph(),
	swatchColor = LocalHammerColors.current.colorFor(type),
	glyphColor = MaterialTheme.colorScheme.onPrimary,
	name = name,
)

/**
 * A labelled group of [HdReferenceChip]s, wrapping onto as many rows as it needs:
 *
 *     CHARACTERS
 *     [☉│ Alice      ×]  [☉│ White Rabbit ×]
 *     [☉│ Cheshire Cat ×]
 *
 * A null [onAction] shows the chips without their trailing action. Callers stack one bucket per
 * group (see `ReferenceBucket`) and space them themselves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdReferenceChipBucket(
	label: String,
	chips: List<HdReferenceChipModel>,
	onClick: (HdReferenceChipModel) -> Unit,
	onAction: ((HdReferenceChipModel) -> Unit)?,
	actionContentDescription: String,
	modifier: Modifier = Modifier,
	variant: HdReferenceChipVariant = HdReferenceChipVariant.Active,
) {
	Column(modifier = modifier) {
		HdMonoLabel(text = label)
		SpacerM()
		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
			verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
		) {
			for (chip in chips) {
				key(chip.key) {
					HdReferenceChip(
						glyph = chip.glyph,
						swatchColor = chip.swatchColor,
						glyphColor = chip.glyphColor,
						name = chip.name,
						onClick = { onClick(chip) },
						onAction = onAction?.let { action -> { action(chip) } },
						actionContentDescription = actionContentDescription,
						variant = variant,
					)
				}
			}
		}
	}
}
