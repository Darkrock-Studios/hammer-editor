package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
fun TimeLineUi(
	component: TimeLine,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.stack.subscribeAsState()

	BoxWithConstraints(modifier = modifier) {
		Children(
			stack = state,
			modifier = Modifier,
			animation = stackAnimation { _ -> fade() },
		) {
			when (val child = it.instance) {
				is TimeLine.Destination.TimeLineOverviewDestination -> {
					TimeLineOverviewUi(
						component = child.component,
						scope = scope,
						showCreate = component::showCreateEvent,
						viewEvent = component::showViewEvent
					)
				}

				is TimeLine.Destination.ViewEventDestination -> {
					ViewTimeLineEventUi(
						modifier = Modifier.align(Alignment.TopCenter),
						component = child.component,
						scope = scope,
						rootSnackbar = rootSnackbar,
					)
				}

				is TimeLine.Destination.CreateEventDestination -> {
					CreateTimeLineEventUi(
						component = child.component,
						scope = scope,
						modifier = Modifier.align(Alignment.TopCenter),
						rootSnackbar = rootSnackbar,
					)
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
				Icon(Icons.Default.Create, MR.strings.timeline_create_event_button.get())
			}
		}

		is TimeLine.Destination.CreateEventDestination -> {

		}

		is TimeLine.Destination.ViewEventDestination -> {

		}
	}
}