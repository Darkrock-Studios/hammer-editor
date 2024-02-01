package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.time.delay
import java.time.Duration

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeNux(swipeableState: SwipeableState<SwipeState>, shouldNux: Boolean) {
	if (shouldNux) {
		LaunchedEffect(key1 = swipeableState) {
			val animSpec = spring<Float>(
				dampingRatio = Spring.DampingRatioMediumBouncy,
				stiffness = Spring.StiffnessLow
			)

			delay(Duration.ofMillis(500))
			swipeableState.animateTo(SwipeState.Swiped, animSpec)
			delay(Duration.ofMillis(500))
			swipeableState.animateTo(SwipeState.Initial)
		}
	}
}