package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors

/**
 * Amber hairline callout: mono [label] over a body [message].
 *
 * ```
 *   ┌──────────────────────────────┐
 *   │ [!]  LABEL                   │
 *   │      Message body.           │
 *   └──────────────────────────────┘
 * ```
 *
 * Non-blocking, unlike an error: the action it warns about stays available.
 */
@Composable
fun HdWarningNotice(
	label: String,
	message: String,
	modifier: Modifier = Modifier,
) {
	val amber = LocalHammerColors.current.warning
	Row(
		modifier = modifier
			.fillMaxWidth()
			.border(width = Dp.Hairline, color = amber, shape = RectangleShape)
			.padding(horizontal = 10.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.Top,
	) {
		HdWarnGlyph(size = 16.dp)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			HdMonoLabel(text = label, color = amber)
			Text(
				text = message,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}
