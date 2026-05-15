package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors

/**
 * Square-cornered alert badge for transient state — defaults to the
 * "unsaved" / dirty buffer indicator. Replaces M3 `Badge` in places where
 * the editor chrome should stay in the hairline + square-corner
 * vocabulary instead of the rounded MD3 default.
 *
 *     ┌──────────┐
 *     │ UNSAVED  │
 *     └──────────┘
 *
 * Uses `LocalHammerColors.danger` for the fill so the meaning is
 * semantic, not chrome — drop in for "Conflict", "Pending", etc. by
 * passing different [text].
 */
@Composable
fun HdUnsavedBadge(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = LocalHammerColors.current.danger,
	contentColor: Color = MaterialTheme.colorScheme.onPrimary,
	contentPadding: PaddingValues = DefaultBadgePadding,
) {
	Box(
		modifier = modifier
			.background(color, RectangleShape)
			.padding(contentPadding),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text.uppercase(),
			style = MaterialTheme.typography.labelSmall,
			color = contentColor,
		)
	}
}

private val DefaultBadgePadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
