package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Small-caps mono label — the most-used primitive in the design system.
 *
 * Renders [text] uppercased in [MaterialTheme.typography.labelSmall] (which
 * `HammerTypography` configures to monospace + extra letter spacing). Use
 * for column headers, metadata keys, navigation labels, chip labels — any
 * "manuscript ledger" annotation in the UI.
 *
 * Defaults to `onSurfaceVariant` (muted) since labels are typically used
 * as supporting text. Pass an explicit [color] for bright/branded usage,
 * or [style] = `labelMedium` for larger annotations.
 */
@Composable
fun HdMonoLabel(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
	style: TextStyle = MaterialTheme.typography.labelSmall,
) {
	Text(
		text = text.uppercase(),
		modifier = modifier,
		color = color,
		style = style,
	)
}
