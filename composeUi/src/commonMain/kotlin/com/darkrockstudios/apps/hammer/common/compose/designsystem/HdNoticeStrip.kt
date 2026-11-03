package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * hairline progress bar, optional [content] such as a chip row, and up to two hairline actions.
 * [trailing] sits after the actions at the strip's end, e.g. an [HdClearGlyph] to dismiss it.
 * Hairline rules top and bottom over a `surfaceContainerLow` fill separate it from the page
 * without elevation.
 *
 *     ──────────────────────────────────────────────────────
 *       Title                              [ SECONDARY ] [ PRIMARY ] ×
 *       detail
 *     ──────────────────────────────────────────────────────
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
	trailing: (@Composable () -> Unit)? = null,
	content: (@Composable ColumnScope.() -> Unit)? = null,
) {
	HdNoticeStrip(
		modifier = modifier,
		primaryLabel = primaryLabel,
		onPrimary = onPrimary,
		secondaryLabel = secondaryLabel,
		onSecondary = onSecondary,
		actionsEnabled = actionsEnabled,
		trailing = trailing,
	) {
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
			content?.invoke(this)
		}
	}
}

/**
 * [HdNoticeStrip] with the caller's own [body] where the title and detail would be, such as a
 * thumbnail beside a few lines of text. With [actionsBelow] the actions take a row of their own
 * under the body, end-aligned, for a width too narrow to hold both side by side.
 */
@Composable
fun HdNoticeStrip(
	modifier: Modifier = Modifier,
	primaryLabel: String? = null,
	onPrimary: () -> Unit = {},
	secondaryLabel: String? = null,
	onSecondary: () -> Unit = {},
	actionsEnabled: Boolean = true,
	trailing: (@Composable () -> Unit)? = null,
	actionsBelow: Boolean = false,
	body: @Composable () -> Unit,
) {
	val rule = MaterialTheme.colorScheme.outlineVariant
	val actions: @Composable () -> Unit = {
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
		trailing?.invoke()
	}
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow),
	) {
		HorizontalDivider(thickness = Dp.Hairline, color = rule)
		if (actionsBelow) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				body()
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
				) {
					actions()
				}
			}
		} else {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Box(modifier = Modifier.weight(1f)) { body() }
				actions()
			}
		}
		HorizontalDivider(thickness = Dp.Hairline, color = rule)
	}
}
