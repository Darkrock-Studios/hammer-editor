package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

enum class HdReferenceChipVariant {
	/** Default — the entry is currently a confirmed reference; action removes it. */
	Active,

	/** The entry has been dismissed (auto-detection declined); action restores it. */
	Dismissed,
}

/**
 * Reference chip — colored type-glyph swatch + entry name + trailing
 * action button. Used by the scene metadata panel for the
 * `confirmedRefs` and `dismissedRefs` lists.
 *
 *     ┌─────┬────────────────────┬───┐
 *     │  ☉  │ Cheshire Cat       │ × │   Active
 *     └─────┴────────────────────┴───┘
 *
 *     ┌─────┬────────────────────┬───┐
 *     │  ◇  │ Tea Party (faded)  │ ↺ │   Dismissed
 *     └─────┴────────────────────┴───┘
 *
 * The swatch fill comes from `LocalHammerColors.colorFor(type)` and the
 * glyph from [EntryType.glyph] so the chip's color and icon are stable
 * regardless of the per-project accent override.
 */
@Composable
fun HdReferenceChip(
	type: EntryType,
	name: String,
	onClick: () -> Unit,
	onAction: () -> Unit,
	actionContentDescription: String,
	modifier: Modifier = Modifier,
	variant: HdReferenceChipVariant = HdReferenceChipVariant.Active,
) {
	val swatchColor = LocalHammerColors.current.colorFor(type)
	val borderColor = MaterialTheme.colorScheme.outlineVariant
	val background = MaterialTheme.colorScheme.surfaceContainer
	val onSurface = MaterialTheme.colorScheme.onSurface
	val onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant
	val isDismissed = variant == HdReferenceChipVariant.Dismissed

	val labelColor = if (isDismissed) onSurfaceMuted else onSurface
	val labelDecoration = if (isDismissed) TextDecoration.LineThrough else TextDecoration.None
	val chipAlpha = if (isDismissed) DismissedAlpha else 1f
	val actionIcon = if (isDismissed) Icons.Filled.Refresh else Icons.Filled.Close

	Row(
		modifier = modifier
			.height(ChipHeight)
			.background(background, RectangleShape)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(0.dp),
	) {
		// Type-color swatch with the entry-type glyph centered inside.
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.widthIn(min = SwatchMinWidth)
				.background(swatchColor)
				.padding(horizontal = 6.dp),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = type.glyph(),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onPrimary,
			)
		}

		// Name — clicking the body navigates to the encyclopedia entry.
		Box(
			modifier = Modifier
				.heightIn(min = ChipHeight)
				.clickable(onClick = onClick)
				.padding(horizontal = 10.dp),
			contentAlignment = Alignment.CenterStart,
		) {
			Text(
				text = name,
				style = MaterialTheme.typography.labelMedium.copy(textDecoration = labelDecoration),
				color = labelColor.copy(alpha = chipAlpha),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		// Trailing action — × for active (dismiss), ↺ for dismissed (restore).
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.clickable(onClick = onAction)
				.padding(horizontal = 6.dp),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				imageVector = actionIcon,
				contentDescription = actionContentDescription,
				tint = onSurfaceMuted,
				modifier = Modifier.size(ActionIconSize),
			)
		}
	}
}

private val ChipHeight: Dp = 26.dp
private val SwatchMinWidth: Dp = 22.dp
private val ActionIconSize: Dp = 14.dp
private const val DismissedAlpha = 0.62f
