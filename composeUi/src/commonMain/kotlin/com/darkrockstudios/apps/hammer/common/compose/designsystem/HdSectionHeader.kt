package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.base.romanNumeral

/**
 * Section header pattern:
 *
 *     § I  Structure  ────────────────────  15 SCENES · 13 CHAPTERS
 *
 * Pass [section] as an Int for auto-romanization, or use the String
 * overload for custom markers (e.g. "—").
 *
 * With [titleMenu] the title is a menu's anchor: one line, ellipsized
 * before it crowds out the rule, followed by a `▾` chevron, and the whole
 * title calls [onTitleClick]. The caller owns the menu.
 *
 *     § IV  Before the eve… ▾  ──  13 ENTRIES · 4 TAGS
 */
@Composable
fun HdSectionHeader(
	section: Int,
	title: String,
	modifier: Modifier = Modifier,
	onTitleClick: (() -> Unit)? = null,
	titleMenu: Boolean = false,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	HdSectionHeader(
		marker = romanNumeral(section),
		title = title,
		modifier = modifier,
		onTitleClick = onTitleClick,
		titleMenu = titleMenu,
		trailing = trailing,
	)
}

@Composable
fun HdSectionHeader(
	marker: String,
	title: String,
	modifier: Modifier = Modifier,
	onTitleClick: (() -> Unit)? = null,
	titleMenu: Boolean = false,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = "§ $marker",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		val clickable = if (onTitleClick != null) {
			Modifier.clickable(role = Role.Button, onClick = onTitleClick)
		} else {
			Modifier
		}
		if (titleMenu) {
			TitleAndRule(modifier = Modifier.weight(1f)) {
				Row(
					modifier = clickable.padding(vertical = 4.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						softWrap = false,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					HdMonoLabel(text = "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			}
		} else {
			Text(
				text = title,
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = clickable,
			)
			HorizontalDivider(
				modifier = Modifier
					.weight(1f)
					.padding(horizontal = 4.dp),
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
		trailing()
	}
}

/**
 * The title, then the rule in what is left. A Row cannot do this: a weighted title is capped at its
 * share of the width, and an unweighted one leaves the rule nothing.
 */
@Composable
private fun TitleAndRule(modifier: Modifier, content: @Composable () -> Unit) {
	Layout(content = content, modifier = modifier) { measurables, constraints ->
		val gap = 16.dp.roundToPx()
		val inset = 4.dp.roundToPx()
		val minRule = 24.dp.roundToPx()
		val bounded = constraints.hasBoundedWidth
		val width = constraints.maxWidth
		val titleMax = if (bounded) (width - gap - minRule - inset).coerceAtLeast(0) else Constraints.Infinity
		val title = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = titleMax))
		val ruleWidth = if (bounded) (width - title.width - gap - inset).coerceAtLeast(0) else minRule
		val rule = measurables[1].measure(constraints.copy(minWidth = ruleWidth, maxWidth = ruleWidth, minHeight = 0))
		val height = constraints.constrainHeight(maxOf(title.height, rule.height))
		layout(constraints.constrainWidth(title.width + gap + ruleWidth + inset), height) {
			title.placeRelative(0, (height - title.height) / 2)
			rule.placeRelative(title.width + gap, (height - rule.height) / 2)
		}
	}
}
