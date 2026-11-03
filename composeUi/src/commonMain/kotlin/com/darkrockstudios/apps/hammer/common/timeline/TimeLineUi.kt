package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.compose.RootFabState
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFab
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.timeline_create_event_button
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TIME_LINE_EVENT_DIALOG_TAG = "timeline-event-dialog"

private const val DIALOG_FADE_IN_MS = 180

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TimeLineUi(
	component: TimeLine,
	rootSnackbar: RootSnackbarHostState,
	rootFab: RootFabState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.stack.subscribeAsState()
	val dialog by component.eventDialog.subscribeAsState()

	SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
		Box(modifier = Modifier.fillMaxSize()) {
			ChildStack(
				stack = state,
				modifier = Modifier,
				animation = stackAnimation(
					animator = fade(),
					predictiveBackParams = {
						PredictiveBackParams(
							backHandler = component.backHandler,
							onBack = component::onBack,
						)
					},
				),
			) { child ->
				when (val dest = child.instance) {
					is TimeLine.Destination.TimeLineOverviewDestination -> {
						TimeLineOverviewUi(
							component = dest.component,
							scope = scope,
							showCreate = component::showCreateEvent,
							viewEvent = component::showViewEvent,
							openEvent = component::showEventDialog,
							openedEventId = dialog.child?.instance?.eventId,
							rootFab = rootFab,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack,
						)
					}

					is TimeLine.Destination.ViewEventDestination -> {
						ViewTimeLineEventUi(
							modifier = Modifier.align(Alignment.TopCenter),
							component = dest.component,
							scope = scope,
							rootSnackbar = rootSnackbar,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack,
						)
					}

					is TimeLine.Destination.CreateEventDestination -> {
						CreateTimeLineEventUi(
							component = dest.component,
							scope = scope,
							modifier = Modifier.align(Alignment.TopCenter),
							rootSnackbar = rootSnackbar,
						)
					}
				}
			}

			EventDialog(component = component, scope = scope, rootSnackbar = rootSnackbar)
		}
	}
}

/**
 * The event opened from the drawing, over the drawing on a scrim. Back (Escape on desktop) or a tap
 * on the scrim closes it, and a predictive back gesture shrinks it as it pulls. A span the drawing
 * can frame gets a Frame action that frames it and closes the dialog.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.EventDialog(
	component: TimeLine,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
) {
	val slot by component.eventDialog.subscribeAsState()
	AnimatedContent(
		targetState = slot.child?.instance,
		modifier = Modifier.fillMaxSize(),
		// No size transform: the scrim fills the pane from the first frame rather than growing into it.
		transitionSpec = {
			(fadeIn(tween(DIALOG_FADE_IN_MS)) togetherWith fadeOut(tween(DIALOG_MORPH_MS))).using(null)
		},
		contentKey = { it?.eventId },
		label = "timeline-event-dialog",
	) { dialog ->
		if (dialog == null) return@AnimatedContent
		val backProgress = rememberDialogBackProgress(component, dialog, isOpen = slot.child?.instance === dialog)
		val eventId = dialog.eventId
		val canFrame = remember(dialog) { component.canFrameEvent(eventId) }
		ViewTimeLineEventUi(
			component = dialog,
			scope = scope,
			rootSnackbar = rootSnackbar,
			sharedTransitionScope = this@EventDialog,
			animatedVisibilityScope = this,
			dialog = true,
			dialogBackProgress = { backProgress.value },
			onFrame = if (canFrame) {
				{ component.frameEvent(eventId) }
			} else {
				null
			},
			modifier = Modifier.testTag(TIME_LINE_EVENT_DIALOG_TAG),
		)
	}
}


/**
 * Follows a predictive back gesture over [dialog] while it is [isOpen] and not editing: the progress
 * rises as the gesture pulls, eases back to zero if it is cancelled, and holds where it was when the
 * gesture commits, so the closing card shrinks on from there. An edit keeps its own back handling,
 * which asks before discarding.
 */
@Composable
private fun rememberDialogBackProgress(component: TimeLine, dialog: ViewTimeLineEvent, isOpen: Boolean): Animatable<Float, *> {
	val progress = remember(dialog) { Animatable(0f) }
	val scope = rememberCoroutineScope()
	val editing = dialog.state.subscribeAsState().value.isEditing
	val callback = remember(dialog) {
		BackCallback(
			onBackProgressed = { event -> scope.launch { progress.snapTo(event.progress) } },
			onBackCancelled = { scope.launch { progress.animateTo(0f) } },
			onBack = component::closeEventDialog,
		)
	}
	SideEffect { callback.isEnabled = isOpen && !editing }
	DisposableEffect(callback) {
		component.backHandler.register(callback)
		onDispose { component.backHandler.unregister(callback) }
	}
	return progress
}

@Composable
fun TimelineFab(
	component: TimeLine,
	rootFab: RootFabState,
	modifier: Modifier = Modifier,
) {
	val stack by component.stack.subscribeAsState()
	val dialog by component.eventDialog.subscribeAsState()
	when (stack.active.instance) {
		is TimeLine.Destination.TimeLineOverviewDestination -> {
			if (dialog.child == null) {
				OverviewFab(scrolledDown = rootFab.scrolledDown, onCreate = component::showCreateEvent, modifier = modifier)
			}
		}

		is TimeLine.Destination.CreateEventDestination -> {

		}

		is TimeLine.Destination.ViewEventDestination -> {

		}
	}
}

/** Slides away while the reader scrolls down and returns as they scroll up. */
@Composable
private fun OverviewFab(
	scrolledDown: Boolean,
	onCreate: () -> Unit,
	modifier: Modifier = Modifier,
) {
	AnimatedVisibility(
		visible = !scrolledDown,
		enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
		exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
	) {
		HdFab(
			onClick = onCreate,
			icon = Icons.Default.Create,
			contentDescription = Res.string.timeline_create_event_button.get(),
			modifier = modifier.testTag(TIME_LINE_CREATE_TAG),
		)
	}
}
