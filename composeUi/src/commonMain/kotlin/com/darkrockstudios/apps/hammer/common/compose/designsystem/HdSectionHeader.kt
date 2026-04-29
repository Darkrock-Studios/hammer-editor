package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders the dashboard-mock section header pattern:
 *
 *     § I  Structure  ────────────────────  15 SCENES · 13 CHAPTERS
 *
 * The roman marker is rendered in mono small-caps, the title in
 * headlineSmall, a hairline divider fills the remaining row, and the
 * optional [trailing] slot holds right-aligned metadata text — typically
 * an [HdMonoLabel].
 *
 * Pass [section] as an Int for an automatic roman numeral, or use the
 * String overload for custom markers.
 */
@Composable
fun HdSectionHeader(
	section: Int,
	title: String,
	modifier: Modifier = Modifier,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	HdSectionHeader(
		marker = romanNumeral(section),
		title = title,
		modifier = modifier,
		trailing = trailing,
	)
}

@Composable
fun HdSectionHeader(
	marker: String,
	title: String,
	modifier: Modifier = Modifier,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = "§ $marker",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = title,
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HorizontalDivider(
			modifier = Modifier
				.weight(1f)
				.padding(horizontal = 4.dp),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		trailing()
	}
}
