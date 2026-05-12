package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Square primary-fill button: mono [prefix] (e.g. `"USE"`) at reduced
 * opacity followed by body-weight [label] in `onPrimary`. The filled
 * complement to [HdHairlineButton].
 */
@Composable
fun HdPrimaryAction(
	prefix: String,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val fillColor = if (enabled) MaterialTheme.colorScheme.primary
	else MaterialTheme.colorScheme.surfaceContainerHigh
	val textColor = if (enabled) MaterialTheme.colorScheme.onPrimary
	else MaterialTheme.colorScheme.onSurfaceVariant
	val borderColor = if (enabled) MaterialTheme.colorScheme.outline
	else MaterialTheme.colorScheme.outlineVariant
	Row(
		modifier = modifier
			.heightIn(min = 30.dp)
			.background(fillColor, RectangleShape)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		HdMonoLabel(
			text = prefix,
			color = textColor.copy(alpha = 0.7f),
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = textColor,
		)
	}
}
