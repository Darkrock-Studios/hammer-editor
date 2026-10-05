package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * A full-width notice pinned above a list: a title line, an optional detail line, an optional
 * hairline progress bar, and up to two hairline actions. Hairline rules top and bottom over a
 * `surfaceContainerLow` fill separate it from the page without elevation.
 */
@Composable
fun HdNoticeStrip(
	title: String,
	modifier: Modifier = Modifier,
	detail: String? = null,
	progress: Float? = null,
	primaryLabel: String? = null,
	onPrimary: () -> Unit = {},
	secondaryLabel: String? = null,
	onSecondary: () -> Unit = {},
	actionsEnabled: Boolean = true,
) {
	val rule = MaterialTheme.colorScheme.outlineVariant
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow),
	) {
		HorizontalDivider(thickness = Dp.Hairline, color = rule)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
				if (detail != null) {
					Text(
						text = detail,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				if (progress != null) {
					HdHairlineProgressBar(
						progress = progress,
						modifier = Modifier.padding(top = 4.dp),
					)
				}
			}
			if (secondaryLabel != null) {
				HdHairlineButton(
					label = secondaryLabel,
					onClick = onSecondary,
					enabled = actionsEnabled,
				)
			}
			if (primaryLabel != null) {
				HdHairlineButton(
					label = primaryLabel,
					onClick = onPrimary,
					emphasised = true,
					enabled = actionsEnabled,
				)
			}
		}
		HorizontalDivider(thickness = Dp.Hairline, color = rule)
	}
}
