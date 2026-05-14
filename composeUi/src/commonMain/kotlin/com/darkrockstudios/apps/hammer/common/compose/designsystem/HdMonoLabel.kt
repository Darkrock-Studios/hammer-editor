package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Mono small-caps label — the design system's annotation primitive.
 * Auto-uppercases [text]; defaults to `onSurfaceVariant`.
 */
@Composable
fun HdMonoLabel(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
	style: TextStyle = MaterialTheme.typography.labelSmall,
	maxLines: Int = Int.MAX_VALUE,
	softWrap: Boolean = true,
	overflow: TextOverflow = TextOverflow.Clip,
	textAlign: TextAlign? = null,
) {
	Text(
		text = text.uppercase(),
		modifier = modifier,
		color = color,
		style = style,
		maxLines = maxLines,
		softWrap = softWrap,
		overflow = overflow,
		textAlign = textAlign,
	)
}
