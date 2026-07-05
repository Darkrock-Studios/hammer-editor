package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView

/**
 * The text-blob card of a browse grid (Notes, Story Ideas): mono meta masthead over a hairline
 * rule, a markdown preview, and an optional [HdTagChip] row. Structure comes from the hairline
 * border and surface tone, never elevation.
 *
 *     ┌──────────────────────────────────┐
 *     │ 03 JUL `26              256 W    │   ← mono meta row ([metaStart] / [metaEnd])
 *     │──────────────────────────────────│
 *     │ <header slot: title, stamps>     │
 *     │ Markdown preview of the body …   │
 *     │ # gothic  # coastal              │
 *     └──────────────────────────────────┘
 *
 * [surfaceModifier], [metaStartModifier] and [markdownModifier] exist so callers can attach
 * shared-element transitions (and test tags) to the card, date label, and body without the card
 * knowing about transition scopes. [surfaceModifier] is applied after the background and border
 * so the whole painted card participates in the transition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdMarkdownCard(
	markdown: String,
	metaStart: String,
	metaEnd: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	surfaceModifier: Modifier = Modifier,
	metaStartModifier: Modifier = Modifier,
	markdownModifier: Modifier = Modifier,
	tags: Set<String> = emptySet(),
	activeTags: Set<String> = emptySet(),
	onTagClick: (String) -> Unit = {},
	header: (@Composable ColumnScope.() -> Unit)? = null,
) {
	// `wrapContentHeight()` is defensive — staggered grid items already give children unbounded
	// height, but this guarantees the card sizes to its content even when a sibling modifier
	// propagates a max.
	Column(
		modifier = modifier
			.fillMaxWidth()
			.wrapContentHeight()
			.background(MaterialTheme.colorScheme.surfaceContainerLow, RectangleShape)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.then(surfaceModifier)
			.clickable(onClick = onClick),
	) {
		// Editor-saved text commonly ends with a trailing `\n`; the markdown pipeline splits on
		// newlines, so a trailing newline becomes an empty trailing line and shows up as a blank
		// gap at the bottom of the card. Trim defensively.
		val previewMarkdown = remember(markdown) { markdown.trim() }

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = metaStart,
				modifier = metaStartModifier,
			)
			Spacer(modifier = Modifier.weight(1f))
			HdMonoLabel(text = metaEnd)
		}

		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		header?.invoke(this)

		MarkdownView(
			markdown = previewMarkdown,
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					horizontal = Ui.Padding.XL,
					vertical = Ui.Padding.L,
				)
				.then(markdownModifier),
		)

		if (tags.isNotEmpty()) {
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.L,
						end = Ui.Padding.L,
						bottom = Ui.Padding.L,
					),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				tags.sorted().forEach { tag ->
					val isActive = tag in activeTags
					HdTagChip(
						label = tag,
						active = isActive,
						onClick = { onTagClick(tag) },
					)
				}
			}
		}
	}
}
