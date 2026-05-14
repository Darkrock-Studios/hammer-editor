package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Catalogue-card entity-id greeble: a short, type-prefixed, zero-padded
 * identifier that anchors a detail surface to its row in the system.
 * Sits next to dates and counters in folio footers, masthead stamp
 * rows, and metadata panels.
 *
 *     ENT-034     SCN-12     NOTE-007
 *
 * Reads as the same handwriting as every other [HdMonoLabel] — small
 * caps mono on `onSurfaceVariant` — so a row of these alongside word
 * counts and tag totals stays visually coherent.
 */
@Composable
fun HdEntityId(
	prefix: String,
	id: Int,
	modifier: Modifier = Modifier,
	padTo: Int = 3,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
	HdMonoLabel(
		text = "$prefix-${id.toString().padStart(padTo, '0')}",
		modifier = modifier,
		color = color,
	)
}
