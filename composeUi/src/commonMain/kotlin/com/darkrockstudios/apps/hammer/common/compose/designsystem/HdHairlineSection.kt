package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dashboard section: [HdSectionHeader] + content + trailing hairline
 * divider. Use [HdPlainSection] when the section has no § marker.
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
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(contentSpacing),
	) {
		HdSectionHeader(
			section = section,
			title = title,
			modifier = Modifier.fillMaxWidth(),
			trailing = headerTrailing,
		)
		content()
		HorizontalDivider(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 8.dp),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
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
		HorizontalDivider(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 8.dp),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}
