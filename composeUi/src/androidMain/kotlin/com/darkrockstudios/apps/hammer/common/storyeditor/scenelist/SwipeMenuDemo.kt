package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * While [demonstrate] is set, slides the row open to reveal its menu icon and back again, on a loop,
 * until the user touches the row; apply the returned modifier to the row to watch for that touch.
 * Driving the state with animateTo skips confirmStateChange, so the menu itself never opens.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun swipeMenuDemo(swipeableState: SwipeableState<SwipeState>, demonstrate: Boolean): Modifier {
	var touched by remember(demonstrate) { mutableStateOf(false) }

	LaunchedEffect(swipeableState, demonstrate, touched) {
		if (demonstrate && !touched) {
			val reveal = spring<Float>(
				dampingRatio = Spring.DampingRatioMediumBouncy,
				stiffness = Spring.StiffnessLow,
			)
			while (true) {
				delay(DEMO_PAUSE_MS)
				swipeableState.animateTo(SwipeState.Swiped, reveal)
				delay(DEMO_HOLD_MS)
				swipeableState.animateTo(SwipeState.Initial)
				delay(DEMO_PAUSE_MS)
			}
		} else if (!touched && swipeableState.offset.value != 0f) {
			// Ending the demo can cancel it mid-slide; after a touch the user's own swipe settles the row.
			swipeableState.animateTo(SwipeState.Initial)
		}
	}

	// Watches without consuming, so the row's own swipe and drag still get every event.
	return Modifier.pointerInput(demonstrate) {
		awaitPointerEventScope {
			while (true) {
				if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touched = true
			}
		}
	}
}

private const val DEMO_PAUSE_MS = 700L
private const val DEMO_HOLD_MS = 900L
