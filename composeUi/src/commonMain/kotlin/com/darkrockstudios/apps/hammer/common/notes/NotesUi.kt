package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.focus.FocusFirstPaneOnArrival
import com.darkrockstudios.apps.hammer.common.compose.focus.PaneId
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeTwiceToLeave
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeToContainer
import com.darkrockstudios.apps.hammer.common.compose.focus.focusPane
import com.darkrockstudios.apps.hammer.common.compose.focus.rememberListCursorState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesUi(
	component: Notes,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val stack by component.stack.subscribeAsState()
	val destination = stack.active.configuration
	val browseCursor = rememberListCursorState()
	LaunchedEffect(destination) {
		if (destination is Notes.Config.ViewNoteConfig) browseCursor.placeOn(destination.noteId)
	}
	FocusFirstPaneOnArrival(destination, enabled = destination is Notes.Config.BrowseNotesConfig)

	SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
		Box(modifier = Modifier.fillMaxSize()) {
			ChildStack(
				stack = stack,
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
					is Notes.Destination.BrowseNotesDestination -> {
						BrowseNotesUi(
							component = dest.component,
							modifier = Modifier.focusPane(PaneId.List),
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack,
							cursor = browseCursor,
						)
					}

					is Notes.Destination.ViewNoteDestination -> {
						ViewNoteUi(
							component = dest.component,
							modifier = Modifier.align(Alignment.TopCenter)
								.escapeToContainer(PaneId.Detail)
								.focusPane(PaneId.Detail),
							rootSnackbar = rootSnackbar,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack
						)
					}

					is Notes.Destination.CreateNoteDestination -> {
						CreateNoteUi(
							dest.component,
							Modifier.escapeTwiceToLeave().focusPane(PaneId.Detail),
							rootSnackbar,
						)
					}
				}
			}
		}
	}
}

@Composable
fun NotesFab(
	component: Notes,
	modifier: Modifier,
) {
	val stack = component.stack.subscribeAsState()
	when (val dest = stack.value.active.instance) {
		is Notes.Destination.BrowseNotesDestination -> {
			BrowseNotesFab(dest.component, modifier)
		}

		is Notes.Destination.ViewNoteDestination -> {

		}

		is Notes.Destination.CreateNoteDestination -> {

		}
	}
}