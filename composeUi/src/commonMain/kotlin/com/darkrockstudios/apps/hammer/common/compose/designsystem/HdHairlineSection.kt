package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dashboard section: a left-side hairline bracket runs the full height of
 * the section, marrying the [HdSectionHeader] to its content like a
 * marginalia rule on a manuscript page. Content is inset to clear the rule.
 *
 * The bracket is drawn via [drawBehind] rather than a child [VerticalDivider]
 * with IntrinsicSize.Min — that approach forces intrinsic measurement of
 * descendants, which crashes when any child is a SubcomposeLayout (KoalaPlot
 * PieChart, lazy lists, BoxWithConstraints, etc.).
 */
@Composable
fun HdHairlineSection(
	section: Int,
	title: String,
	modifier: Modifier = Modifier,
	headerTrailing: @Composable RowScope.() -> Unit = {},
	contentSpacing: Dp = 16.dp,
	content: @Composable ColumnScope.() -> Unit,
) {
	val dividerColor = MaterialTheme.colorScheme.outlineVariant
	val strokePx = with(LocalDensity.current) { Dp.Hairline.toPx().coerceAtLeast(1f) }
	Column(
		modifier = modifier
			.fillMaxWidth()
			.drawBehind {
				val x = strokePx / 2f
				drawLine(
					color = dividerColor,
					start = Offset(x, 0f),
					end = Offset(x, size.height),
					strokeWidth = strokePx,
				)
			}
			.padding(start = 16.dp),
		verticalArrangement = Arrangement.spacedBy(contentSpacing),
	) {
		HdSectionHeader(
			section = section,
			title = title,
			modifier = Modifier.fillMaxWidth(),
			trailing = headerTrailing,
		)
		content()
	}
}

/**
 * Same vertical structure as [HdHairlineSection] but without the section
 * header — used for the unmarked top stats strip in the dashboard.
 */
@Composable
fun HdPlainSection(
	modifier: Modifier = Modifier,
	contentSpacing: Dp = 16.dp,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(contentSpacing),
	) {
		content()
	}
}
