package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Crumb-row back link — a leftwards arrow followed by a mono small-caps label,
 * rendered as a single [HdMonoLabel]. Standardizes the inline `"← LABEL"`
 * pattern that recurs in detail-screen breadcrumbs. The `←` glyph lands on
 * the bundled IBM Plex Mono so it renders consistently on every platform.
 */
@Composable
fun HdCrumbBackLink(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurface,
	onClickLabel: String? = null,
) {
	HdMonoLabel(
		text = "← $label",
		color = color,
		modifier = modifier
			.clickable(
				onClick = onClick,
				role = Role.Button,
				onClickLabel = onClickLabel,
			)
			.padding(vertical = 4.dp),
	)
}
