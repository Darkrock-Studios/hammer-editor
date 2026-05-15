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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Doubled-hairline "catalogue card" — a library-index-card affordance
 * for highlighting a particular section above the rest of the page.
 *
 *     ┌──────────────────────────────────────────────────┐
 *     │ ┌──[ § III · SYNC ]────────────[ CONNECTED ]──┐  │
 *     │ │                                              │  │
 *     │ │   <body content>                             │  │
 *     │ │                                              │  │
 *     │ └─[ KTOR · HTTPS ]─────────[ LAST SYNC 14:32 ]┘  │
 *     └──────────────────────────────────────────────────┘
 *
 * Outer hairline border, 6dp inset, inner hairline border, [contentPadding]
 * for the body. Up to four corner greebles ([topStart], [topEnd],
 * [bottomStart], [bottomEnd]) sit *on* the inner border with a
 * `surface`-colored background that punches the line cleanly.
 *
 * Greebles must encode real values per the design-system "a greeble has
 * to mean something" rule — leave a slot null rather than fabricate.
 */
@Composable
fun HdCatalogueCard(
	modifier: Modifier = Modifier,
	topStart: String? = null,
	topEnd: String? = null,
	bottomStart: String? = null,
	bottomEnd: String? = null,
	contentPadding: Dp = 20.dp,
	content: @Composable ColumnScope.() -> Unit,
) {
	val border = MaterialTheme.colorScheme.outlineVariant
	val surface = MaterialTheme.colorScheme.surface
	Box(
		modifier = modifier
			.fillMaxWidth()
			.border(width = Dp.Hairline, color = border, shape = RectangleShape)
			.padding(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.border(width = Dp.Hairline, color = border, shape = RectangleShape),
		) {
			Column(
				modifier = Modifier.padding(contentPadding),
				content = content,
			)
		}
		CornerGreeble(
			text = topStart,
			alignment = Alignment.TopStart,
			surface = surface,
			modifier = Modifier
				.align(Alignment.TopStart)
				.offset(x = 14.dp, y = (-8).dp),
		)
		CornerGreeble(
			text = topEnd,
			alignment = Alignment.TopEnd,
			surface = surface,
			modifier = Modifier
				.align(Alignment.TopEnd)
				.offset(x = (-14).dp, y = (-8).dp),
		)
		CornerGreeble(
			text = bottomStart,
			alignment = Alignment.BottomStart,
			surface = surface,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.offset(x = 14.dp, y = 8.dp),
		)
		CornerGreeble(
			text = bottomEnd,
			alignment = Alignment.BottomEnd,
			surface = surface,
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.offset(x = (-14).dp, y = 8.dp),
		)
	}
}

@Composable
private fun CornerGreeble(
	text: String?,
	alignment: Alignment,
	surface: Color,
	modifier: Modifier = Modifier,
) {
	if (text == null) return
	Box(
		modifier = modifier
			.background(surface, RectangleShape)
			.padding(horizontal = 6.dp, vertical = 1.dp),
		contentAlignment = alignment,
	) {
		HdMonoLabel(text = "[ $text ]")
	}
}
