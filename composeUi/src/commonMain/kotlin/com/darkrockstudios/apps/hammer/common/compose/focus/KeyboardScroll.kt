package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val LineStep = 48.dp

/**
 * Makes a [androidx.compose.foundation.verticalScroll] area a focus stop that scrolls on Up/Down
 * (a line), PageUp/PageDown (a screen), Home and End. Put it before the `verticalScroll`.
 */
@Composable
fun Modifier.keyboardScroll(state: ScrollState, focusRequester: FocusRequester): Modifier {
	val scope = rememberCoroutineScope()
	val line = with(LocalDensity.current) { LineStep.toPx() }
	return this
		.focusRequester(focusRequester)
		.onKeyEvent { event ->
			if (!event.isBareKeyDown()) return@onKeyEvent false
			val page = state.viewportSize.toFloat()
			when (event.key) {
				// Instant, so a held arrow's repeats don't cancel each other's animation.
				Key.DirectionDown -> scope.launch { state.scrollBy(line) }
				Key.DirectionUp -> scope.launch { state.scrollBy(-line) }
				Key.PageDown -> scope.launch { state.animateScrollBy(page) }
				Key.PageUp -> scope.launch { state.animateScrollBy(-page) }
				Key.MoveHome -> scope.launch { state.animateScrollTo(0) }
				Key.MoveEnd -> scope.launch { state.animateScrollTo(state.maxValue) }
				else -> return@onKeyEvent false
			}
			true
		}
		.focusable()
}
