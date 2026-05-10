package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dashboard section: a left-side hairline bracket runs the full height of
 * the section, marrying the [HdSectionHeader] to its content like a
 * marginalia rule on a manuscript page. Content is inset to clear the rule.
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
	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min),
	) {
		VerticalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Column(
			modifier = Modifier
				.fillMaxWidth()
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
