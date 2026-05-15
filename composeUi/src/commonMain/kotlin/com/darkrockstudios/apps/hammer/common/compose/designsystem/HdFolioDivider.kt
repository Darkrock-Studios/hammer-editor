package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Folio masthead divider — a 2dp `outline` rule, a 2dp gap, then a
 * hairline `outlineVariant` rule. Used directly under section
 * mastheads (e.g. the Create Entry modal header) to give the surface
 * the printed-page feel without any radius.
 *
 *     ════════════════════════════════════════
 *     ────────────────────────────────────────
 */
@Composable
fun HdFolioDivider(modifier: Modifier = Modifier) {
	Column(modifier = modifier.fillMaxWidth()) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(2.dp)
				.background(MaterialTheme.colorScheme.outline),
		)
		Box(modifier = Modifier.height(2.dp))
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(1.dp)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
	}
}
