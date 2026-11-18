package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Mono at regular weight and near-normal tracking: the [HdMetaText] style. */
@Composable
fun hdMetaStyle(): TextStyle =
	MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)

/**
 * Scannable metadata: numbers, dates, counts ("Opened 11 Nov 2026", "35,048 words"). Mono so
 * digits line up down a column, but sentence case and untracked so it reads as words.
 * [HdMonoLabel]'s tracked caps are for true greebles only.
 */
@Composable
fun HdMetaText(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
) {
	Text(text = text, modifier = modifier, style = hdMetaStyle(), color = color, maxLines = maxLines, overflow = overflow)
}
