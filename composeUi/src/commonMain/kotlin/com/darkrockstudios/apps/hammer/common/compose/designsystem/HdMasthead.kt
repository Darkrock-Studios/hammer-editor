package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Dialog masthead row: `§ SECTION  |  meta  |  meta  …    trailing actions`.
 * Leading meta cells render after the section marker, separated by hairline
 * pipes; the [trailing] slot is for clickable mono actions like `× CLOSE` or
 * `? HELP` (use [HdMastheadAction]).
 */
@Composable
fun HdMasthead(
	section: String,
	modifier: Modifier = Modifier,
	leadingMeta: List<String> = emptyList(),
	trailing: @Composable RowScope.() -> Unit = {},
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Row(
			modifier = Modifier.weight(1f),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdMonoLabel(
				text = "§ $section",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				softWrap = false,
				overflow = TextOverflow.Ellipsis,
			)
			for (meta in leadingMeta) {
				MastheadSeparator()
				HdMonoLabel(
					text = meta,
					modifier = Modifier.weight(1f, fill = false),
					maxLines = 1,
					softWrap = false,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
		trailing()
	}
}

/** Trailing masthead link — mono uppercase label with a click target. */
@Composable
fun HdMastheadAction(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	HdMonoLabel(
		text = label,
		color = MaterialTheme.colorScheme.onSurface,
		maxLines = 1,
		softWrap = false,
		modifier = modifier
			.clickable(onClick = onClick)
			.padding(Ui.Padding.S),
	)
}

@Composable
private fun MastheadSeparator() {
	Box(
		modifier = Modifier
			.height(12.dp)
			.width(Dp.Hairline)
			.background(MaterialTheme.colorScheme.outlineVariant),
	)
}
