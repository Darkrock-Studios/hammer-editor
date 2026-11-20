package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass

/**
 * The folio at the foot of a page or view: a hairline, then [greebles](DESIGN_README.md#greebles).
 * Centered and dot-joined by default; [spread] sets the first part at the start and the rest at the end.
 * Phones leave it out: their height is worth more than the flourish.
 *
 *     ─────────────────────────────────────────
 *               ACCOUNT SETTINGS  ·  §§ 3
 */
@Composable
fun HdFolioFooter(
	parts: List<String>,
	modifier: Modifier = Modifier,
	horizontalPadding: Dp = Ui.Padding.XL,
	spread: Boolean = false,
) {
	if (LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact) return
	HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = if (spread) {
			Arrangement.spacedBy(Ui.Padding.L)
		} else {
			Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
		},
	) {
		parts.forEachIndexed { index, part ->
			if (spread) {
				if (index == 1) Spacer(Modifier.weight(1f))
			} else if (index > 0) {
				HdMonoLabel(text = "·", color = MaterialTheme.colorScheme.outlineVariant)
			}
			HdMonoLabel(text = part)
		}
	}
}
