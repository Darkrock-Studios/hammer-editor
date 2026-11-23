package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Doubled-hairline "catalogue card": a library-index-card affordance for highlighting one
 * section above the rest of the page, with an optional [caption] greeble set on the inner
 * border's top-left. Under a tonal [HdGrouping] it becomes a region titled [caption].
 *
 *     ┌──────────────────────────────────────┐
 *     │ ┌──[ NOTE ]───────────────────────┐  │
 *     │ │   <body content>                 │  │
 *     │ └──────────────────────────────────┘  │
 *     └──────────────────────────────────────┘
 */
@Composable
fun HdCatalogueCard(
	modifier: Modifier = Modifier,
	caption: String? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	if (LocalHdGrouping.current.isTonal) {
		HdRegion(modifier = modifier, title = caption) { Column(content = content) }
		return
	}
	val border = MaterialTheme.colorScheme.outlineVariant
	Box(
		modifier = modifier
			.fillMaxWidth()
			.border(width = Dp.Hairline, color = border, shape = RectangleShape)
			.padding(6.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.border(width = Dp.Hairline, color = border, shape = RectangleShape)
				.padding(20.dp),
			content = content,
		)
		if (caption != null) {
			Box(
				modifier = Modifier
					.align(Alignment.TopStart)
					.offset(x = 14.dp, y = (-8).dp)
					.background(MaterialTheme.colorScheme.surface, RectangleShape)
					.padding(horizontal = 6.dp, vertical = 1.dp),
			) {
				HdMonoLabel(text = "[ $caption ]")
			}
		}
	}
}
