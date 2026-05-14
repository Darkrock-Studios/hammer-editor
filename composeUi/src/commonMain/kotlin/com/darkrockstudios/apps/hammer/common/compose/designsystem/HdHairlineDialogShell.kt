package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Hairline-bordered modal shell with a title row and close affordance. Pair with
 * [com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog] and pass its
 * `requestDismiss` as [onClose] so the exit animation plays.
 */
@Composable
fun HdHairlineDialogShell(
	title: String,
	onClose: () -> Unit,
	closeContentDescription: String,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Surface(
		modifier = modifier
			.widthIn(min = 320.dp, max = 520.dp)
			.padding(Ui.Padding.L)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		tonalElevation = 0.dp,
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleLarge,
					modifier = Modifier.weight(1f),
				)
				IconButton(onClick = onClose) {
					Icon(
						imageVector = Icons.Filled.Close,
						contentDescription = closeContentDescription,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
			SpacerL()
			content()
		}
	}
}
