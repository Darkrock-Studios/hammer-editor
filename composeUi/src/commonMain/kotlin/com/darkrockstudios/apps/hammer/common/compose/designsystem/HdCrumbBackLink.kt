package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Crumb-row back link — a leftwards arrow followed by a mono small-caps label.
 * Replaces inline `"← LABEL"` strings, which fall back to a tiny glyph on JVM
 * monospace fonts that lack U+2190. The vector icon also mirrors correctly in
 * RTL layouts.
 */
@Composable
fun HdCrumbBackLink(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurface,
	onClickLabel: String? = null,
) {
	Row(
		modifier = modifier
			.clickable(
				onClick = onClick,
				role = Role.Button,
				onClickLabel = onClickLabel,
			)
			.padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = Icons.AutoMirrored.Filled.ArrowBack,
			contentDescription = null,
			tint = color,
			modifier = Modifier.size(14.dp),
		)
		HdMonoLabel(text = label, color = color)
	}
}
