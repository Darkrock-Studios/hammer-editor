package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.timeline_create_event_button

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TimeLineUi(
	component: TimeLine,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.stack.subscribeAsState()

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
		}
	}
}


@Composable
fun TimelineFab(
	component: TimeLine,
	modifier: Modifier = Modifier,
) {
	val stack by component.stack.subscribeAsState()
	when (stack.active.instance) {
		is TimeLine.Destination.TimeLineOverviewDestination -> {
			FloatingActionButton(
				onClick = component::showCreateEvent,
				modifier = modifier.testTag(TIME_LINE_CREATE_TAG)
			) {
				Icon(Icons.Default.Create, Res.string.timeline_create_event_button.get())
			}
		}

		is TimeLine.Destination.CreateEventDestination -> {

		}

		is TimeLine.Destination.ViewEventDestination -> {

		}
	}
}