package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine

@Composable
fun TimeLineUi(component: TimeLine) {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	val state by component.stack.subscribeAsState()

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		Children(
			stack = state,
			modifier = Modifier,
			//animation = stackAnimation { _, _, _ -> fade() },
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
						snackbarHostState = snackbarHostState,
						closeEvent = component::showOverview
					)
				}

				is TimeLine.Destination.CreateEventDestination -> {
					CreateTimeLineEventUi(
						component = child.component,
						scope = scope,
						modifier = Modifier.align(Alignment.TopCenter),
						snackbarHostState = snackbarHostState,
						close = component::showOverview
					)
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}
}
