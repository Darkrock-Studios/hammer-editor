package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import kotlin.math.abs

/**
 * Signed percent-change indicator: `▲ 22% vs last week`.
 * Sign of [percent] picks arrow + success/danger color.
 */
@Composable
fun HdDeltaBadge(
	percent: Float,
	modifier: Modifier = Modifier,
	suffix: String? = null,
	style: TextStyle = MaterialTheme.typography.labelMedium,
) {
	val colors = LocalHammerColors.current
	val isPositive = percent >= 0f
	val arrow = if (isPositive) "▲" else "▼"
	val color = if (isPositive) colors.success else colors.danger
	val rounded = abs(percent).toInt()

	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Text(
			text = "$arrow $rounded%",
			style = style,
			color = color,
		)
		if (suffix != null) {
			Text(
				text = suffix,
				style = style,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
