package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val ENTER_EXIT_MS = 220
private const val ENTER_INITIAL_SCALE = 0.96f
private const val BACK_MIN_SCALE = 0.92f
private const val BACK_MIN_ALPHA = 0.6f
private val BACK_TRANSLATION = 24.dp

/**
 * Scope provided to [AnimatedFullScreenDialog] content. Call [requestDismiss] from close
 * buttons or anywhere the dialog should start its exit animation; the dialog's `onDismissed`
 * lambda fires once the animation completes.
 */
class AnimatedFullScreenDialogScope internal constructor(
	private val transitionState: MutableTransitionState<Boolean>,
) {
	fun requestDismiss() {
		transitionState.targetState = false
	}
}

/**
 * Full-screen modal dialog with fade+scale enter/exit animation and predictive-back gesture
 * support. The Dialog escapes the layout tree, so it covers everything including navigation rails.
 *
 * @param onDismissed invoked after the exit animation finishes — wire this to the actual
 *   navigation/slot dismissal.
 * @param backgroundColor solid color painted under the content while it animates.
 * @param content rendered inside the dialog. Call `requestDismiss()` on the receiver scope to
 *   trigger the animated dismiss flow (e.g. from a toolbar close button).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimatedFullScreenDialog(
	onDismissed: () -> Unit,
	modifier: Modifier = Modifier,
	backgroundColor: Color = MaterialTheme.colorScheme.background,
	content: @Composable AnimatedFullScreenDialogScope.() -> Unit,
) {
	val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
	val backProgress = remember { Animatable(0f) }
	val coroutineScope = rememberCoroutineScope()
	val scope = remember(transitionState) { AnimatedFullScreenDialogScope(transitionState) }

	LaunchedEffect(transitionState.currentState, transitionState.targetState) {
		if (!transitionState.targetState && !transitionState.currentState) {
			onDismissed()
		}
	}

	Dialog(
		onDismissRequest = scope::requestDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		PredictiveBackHandler(enabled = transitionState.targetState) { events ->
			try {
				events.collect { event -> backProgress.snapTo(event.progress) }
				scope.requestDismiss()
			} catch (_: CancellationException) {
				coroutineScope.launch { backProgress.animateTo(0f) }
			}
		}

		AnimatedVisibility(
			visibleState = transitionState,
			enter = fadeIn(tween(ENTER_EXIT_MS)) +
				scaleIn(initialScale = ENTER_INITIAL_SCALE, animationSpec = tween(ENTER_EXIT_MS)),
			exit = fadeOut(tween(ENTER_EXIT_MS)) +
				scaleOut(targetScale = ENTER_INITIAL_SCALE, animationSpec = tween(ENTER_EXIT_MS)),
		) {
			Box(
				modifier = modifier
					.fillMaxSize()
					.graphicsLayer {
						val p = backProgress.value
						val s = lerp(1f, BACK_MIN_SCALE, p)
						scaleX = s
						scaleY = s
						translationY = BACK_TRANSLATION.toPx() * p
						alpha = lerp(1f, BACK_MIN_ALPHA, p)
					}
					.background(backgroundColor)
			) {
				scope.content()
			}
		}
	}
}
