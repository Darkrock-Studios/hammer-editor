package com.darkrockstudios.apps.hammer.common.preview.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.ViewNote
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.notes.ViewNoteUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import kotlin.time.Clock

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun ViewNoteUiPreview() {
	val component = object : ViewNote {
		override val state: Value<ViewNote.State>
			get() = MutableValue(
				ViewNote.State(
					projectDef = fakeProjectDef(),
					note = fakeNoteContent(),
				)
			)
		override val noteText: Value<String>
			get() = MutableValue("This is a test note.")

		override fun discardEdit() {}
		override fun onContentChanged(newContent: String) {}
		override suspend fun deleteNote(id: Int) {}
		override fun confirmDelete() {}
		override fun dismissConfirmDelete() {}
		override suspend fun storeNoteUpdate() {}
		override fun closeNote() {}
		override fun beginEdit() {}
		override fun isEditingAndDirty(): Boolean = false
		override fun confirmDiscard() {}
		override fun cancelDiscard() {}
		override fun confirmClose() {}
		override fun cancelClose() {}
	}

	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				ViewNoteUi(
					component = component,
					modifier = Modifier,
					rootSnackbar = rootSnackbar,
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedVisibility,
				)
			}
		}
	}
}

private fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

private fun fakeNoteContent(): NoteContent = NoteContent(
	id = 1,
	created = Clock.System.now(),
	content = "This is a test note."
)
