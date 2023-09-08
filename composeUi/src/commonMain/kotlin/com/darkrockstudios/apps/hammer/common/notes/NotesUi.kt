package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState

@Composable
fun NotesUi(
	component: Notes,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val stack by component.stack.subscribeAsState()
	Children(
		stack = stack,
		modifier = Modifier,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val dest = it.instance) {
			is Notes.Destination.BrowseNotesDestination -> {
				BrowseNotesUi(dest.component, modifier)
			}

			is Notes.Destination.ViewNoteDestination -> {
				ViewNoteUi(dest.component, modifier, rootSnackbar)
			}

			is Notes.Destination.CreateNoteDestination -> {
				CreateNoteUi(dest.component, modifier, rootSnackbar)
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