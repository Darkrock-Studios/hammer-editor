package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount

/**
 * The tag-count filter row of a browse screen: an "all" cell, a hairline separator, then one
 * [HdTagChip] per tag (`label · count`) in a horizontally scrolling row. Optional [leading]
 * slot (e.g. an inline search field on Expanded widths) and [trailing] slot (e.g. a sort menu),
 * both pinned. [scrollingLeading] opens the scrolling row behind its own hairline, for a second
 * set of filter chips that would not fit pinned on a narrow screen.
 *
 *     [⌕ search] │ ALL · 12 │ # gothic · 3  # coastal · 1 …   [SORT ▾]
 *     ALL · 12 │ ▪ Alice · 4  ▪ Queen · 2 │ # gothic · 3 …     (scrollingLeading)
 *
 * The row always renders even with no tags, so the trailing affordance has a stable home.
 * [allLabel] arrives pre-formatted (`"ALL · 12"`) so the component stays string-resource free.
 */
@Composable
fun HdTagFilterBar(
	tags: List<TagCount>,
	allLabel: String,
	activeTags: Set<String>,
	onToggle: (String) -> Unit,
	onClear: () -> Unit,
	modifier: Modifier = Modifier,
	leading: (@Composable () -> Unit)? = null,
	scrollingLeading: (@Composable () -> Unit)? = null,
	trailing: @Composable () -> Unit = {},
) {
	val allActive = activeTags.isEmpty()
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (leading != null) {
			leading()
			VerticalHairline()
		}
		AllChip(
			label = allLabel,
			active = allActive,
			onClick = onClear,
		)
		VerticalHairline()
		LazyRow(
			modifier = Modifier.weight(1f),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (scrollingLeading != null) {
				item(key = SCROLLING_LEADING_KEY) { scrollingLeading() }
				if (tags.isNotEmpty()) {
					item(key = SCROLLING_LEADING_RULE_KEY) {
						VerticalHairline(modifier = Modifier.padding(horizontal = Ui.Padding.M - 6.dp))
					}
				}
			}
			items(count = tags.size) { i ->
				val (label, count) = tags[i]
				val isActive = label in activeTags
				HdTagChip(
					label = "$label · $count",
					active = isActive,
					onClick = { onToggle(label) },
				)
			}
		}
		trailing()
	}
}

private const val SCROLLING_LEADING_KEY = "scrolling-leading"
private const val SCROLLING_LEADING_RULE_KEY = "scrolling-leading-rule"

@Composable
private fun VerticalHairline(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.height(20.dp)
			.width(Dp.Hairline)
			.background(MaterialTheme.colorScheme.outlineVariant),
	)
}

@Composable
private fun AllChip(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
) {
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	Box(
		modifier = Modifier
			.height(28.dp)
			.background(background, RectangleShape)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.L),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = label, color = labelColor)
	}
}
