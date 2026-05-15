package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Rectangular hairline progress strip with caller-supplied fill so the bar can carry a semantic tone. */
@Composable
fun HdHairlineProgressBar(
	progress: Float,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.primary,
	trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
	height: Dp = 2.dp,
) {
	val clamped = progress.coerceIn(0f, 1f)
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(height)
			.background(trackColor),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(clamped)
				.fillMaxHeight()
				.background(color),
		)
	}
}
