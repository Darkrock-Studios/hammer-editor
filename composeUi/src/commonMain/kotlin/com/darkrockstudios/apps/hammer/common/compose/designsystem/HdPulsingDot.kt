package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** A small square that pulses while something is live, such as an open edit. */
@Composable
fun HdPulsingDot(modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition(label = "hdPulsingDot")
	val alpha by transition.animateFloat(
		initialValue = 1f,
		targetValue = 0.35f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 800),
			repeatMode = RepeatMode.Reverse,
		),
		label = "hdPulsingDotAlpha",
	)
	Box(
		modifier = modifier
			.size(7.dp)
			.alpha(alpha)
			.background(MaterialTheme.colorScheme.primary, RectangleShape),
	)
}
