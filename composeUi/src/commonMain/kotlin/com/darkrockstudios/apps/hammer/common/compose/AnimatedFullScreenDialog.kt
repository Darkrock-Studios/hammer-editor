package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
 * Receiver scope for content of [AnimatedDialogContainer] (and the public dialog wrappers built on
 * top of it). Provides:
 * - [requestDismiss]: trigger the exit animation; the container's `onClosed` fires once it
 *   completes. Wire this to close buttons.
 * - [predictiveBackTransform]: applies the predictive-back gesture scale/translate/alpha to the
 *   modifier you put it on. Apply it to whatever element should "shrink" during the gesture.
 */
class AnimatedDialogScope internal constructor(
	private val onDismissRequest: () -> Unit,
	private val backProgress: Animatable<Float, AnimationVector1D>,
) {
	fun requestDismiss() = onDismissRequest()

	fun Modifier.predictiveBackTransform(): Modifier = graphicsLayer {
		val p = backProgress.value
		val s = lerp(1f, BACK_MIN_SCALE, p)
		scaleX = s
		scaleY = s
		translationY = BACK_TRANSLATION.toPx() * p
		alpha = lerp(1f, BACK_MIN_ALPHA, p)
	}
}

/**
 * Shared shell that animates a [Dialog] in/out and wires predictive-back. Used by
 * [AnimatedFullScreenDialog] and [SimpleDialog] — most callers should reach for one of those
 * rather than this directly.
 *
 * @param isOpen drives the enter/exit transition. Flip to `false` to start the exit animation.
 * @param onDismissRequest fires when the user requests dismissal (close button via
 *   [AnimatedDialogScope.requestDismiss], scrim/ESC via [Dialog.onDismissRequest], or a committed
 *   predictive-back gesture). Callers usually respond by flipping their own `isOpen` state to
 *   false.
 * @param onClosed fires once the exit animation has finished. Use this to actually unmount the
 *   dialog (e.g. clear the rendering state, dismiss the navigation slot).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AnimatedDialogContainer(
	isOpen: Boolean,
	onDismissRequest: () -> Unit,
	onClosed: () -> Unit,
	properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
	content: @Composable AnimatedDialogScope.() -> Unit,
) {
	val transitionState = remember { MutableTransitionState(false) }
	LaunchedEffect(isOpen) { transitionState.targetState = isOpen }

	val backProgress = remember { Animatable(0f) }
	val coroutineScope = rememberCoroutineScope()
	val scope = remember(onDismissRequest) {
		AnimatedDialogScope(onDismissRequest, backProgress)
	}

	LaunchedEffect(transitionState.currentState, transitionState.targetState) {
		if (!transitionState.targetState && !transitionState.currentState) {
			onClosed()
		}
	}

	Dialog(
		onDismissRequest = onDismissRequest,
		properties = properties,
	) {
		PredictiveBackHandler(enabled = transitionState.targetState) { events ->
			try {
				events.collect { event -> backProgress.snapTo(event.progress) }
				onDismissRequest()
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
			scope.content()
		}
	}
}

/**
 * Bare animated modal dialog with fade+scale enter/exit and predictive-back support.
 * Unlike [SimpleDialog] this provides no built-in chrome — the caller supplies their own
 * container (Card, Surface, etc.). Useful when the dialog content already has its own
 * header / close affordance and you don't want SimpleDialog's title bar fighting with it.
 *
 * For a card-with-title-bar dialog, use [SimpleDialog]. For a full-screen one, use
 * [AnimatedFullScreenDialog].
 *
 * @param visible drives the enter/exit transition. Flip to `false` to start the exit
 *   animation. The dialog stays mounted while the exit plays.
 * @param onCloseRequest fires when the user requests dismissal (in-content close,
 *   ESC, scrim tap if [dismissOnTapOutside], or a committed predictive-back gesture).
 *   Typical handler: flip your `visible` state to false.
 * @param dismissOnTapOutside if true, taps on the surrounding scrim trigger an animated
 *   dismiss. If false (the default), the user must use an in-content affordance, ESC, or
 *   the back gesture.
 * @param onDismissed fires after the exit animation completes — use this if you need to
 *   clean up after the dialog has fully unmounted.
 * @param content rendered inside the dialog. Call `requestDismiss()` on the receiver
 *   scope to trigger the animated dismiss flow from inside (e.g. a close button).
 */
@Composable
fun AnimatedDialog(
	visible: Boolean,
	onCloseRequest: () -> Unit,
	modifier: Modifier = Modifier,
	contentAlignment: Alignment = Alignment.Center,
	dismissOnTapOutside: Boolean = false,
	onDismissed: () -> Unit = {},
	content: @Composable AnimatedDialogScope.() -> Unit,
) {
	var renderInternal by remember { mutableStateOf(visible) }
	LaunchedEffect(visible) { if (visible) renderInternal = true }

	if (!renderInternal) return

	AnimatedDialogContainer(
		isOpen = visible,
		onDismissRequest = onCloseRequest,
		onClosed = {
			renderInternal = false
			onDismissed()
		},
	) {
		if (dismissOnTapOutside) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
						onClick = { requestDismiss() },
					),
				contentAlignment = contentAlignment,
			) {
				Box(
					modifier = modifier
						.predictiveBackTransform()
						.clickable(
							interactionSource = remember { MutableInteractionSource() },
							indication = null,
							onClick = {},
						),
					contentAlignment = contentAlignment,
				) {
					content()
				}
			}
		} else {
			Box(
				modifier = modifier.predictiveBackTransform(),
				contentAlignment = contentAlignment,
			) {
				content()
			}
		}
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
@Composable
fun AnimatedFullScreenDialog(
	onDismissed: () -> Unit,
	modifier: Modifier = Modifier,
	backgroundColor: Color = MaterialTheme.colorScheme.background,
	content: @Composable AnimatedDialogScope.() -> Unit,
) {
	var isOpen by remember { mutableStateOf(true) }

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = { isOpen = false },
		onClosed = onDismissed,
	) {
		Box(
			modifier = modifier
				.fillMaxSize()
				.predictiveBackTransform()
				.background(backgroundColor)
		) {
			content()
		}
	}
}
