package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/** The tinted strip under an editor: counts on the left, hints or actions on the right. */
@Composable
fun HdStatusFooter(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		content = content,
	)
}
