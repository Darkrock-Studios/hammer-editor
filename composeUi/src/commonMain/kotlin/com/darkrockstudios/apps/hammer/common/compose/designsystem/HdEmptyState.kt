package com.darkrockstudios.apps.hammer.common.compose.designsystem

import com.darkrockstudios.apps.hammer.common.compose.Ui
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * "Nothing here yet", said kindly: a dashed hairline frame (the same dashed rule as an empty
 * image drop) around a light icon, what's missing, and where to go about it. The dashes say
 * "a place for something"; a solid frame would read as content.
 *
 *     ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
 *              ⧉
 *         No plugins installed
 *      Install them in Settings …
 *     └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 */
@Composable
fun HdEmptyState(
	icon: ImageVector,
	title: String,
	body: String,
	modifier: Modifier = Modifier,
	action: (@Composable () -> Unit)? = null,
) {
	val dash = MaterialTheme.colorScheme.outlineVariant
	Column(
		modifier = modifier
			.fillMaxWidth()
			.drawWithCache {
				val stroke = 1.dp.toPx()
				val style = Stroke(
					width = stroke,
					pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
				)
				onDrawBehind {
					drawRect(
						color = dash,
						topLeft = Offset(stroke / 2, stroke / 2),
						size = Size(size.width - stroke, size.height - stroke),
						style = style,
					)
				}
			}
			.padding(horizontal = 32.dp, vertical = 40.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.outline,
			modifier = Modifier.size(40.dp),
		)
		Text(
			text = title,
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.Center,
		)
		Text(
			text = body,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.widthIn(max = 420.dp),
		)
		if (action != null) action()
	}
}

/** A quiet centred line where a dialog's or panel's list would be. */
@Composable
fun HdEmptyNote(
	text: String,
	modifier: Modifier = Modifier,
	minHeight: Dp = 160.dp,
) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = minHeight)
			.padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
		)
	}
}
