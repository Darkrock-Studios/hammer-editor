package com.darkrockstudios.apps.hammer.common.preview.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.notes.CreateNoteUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

@Preview
@Composable
fun CreateNoteUiPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize(),
			) {
				CreateNoteUi(
					component = component,
					modifier = Modifier,
					rootSnackbar = rememberRootSnackbarHostState(),
				)
			}
		}
	}
}

@Preview
@Composable
fun CreateNoteUiNarrowPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.size(width = 390.dp, height = 780.dp),
			) {
				CreateNoteUi(
					component = component,
					modifier = Modifier,
					rootSnackbar = rememberRootSnackbarHostState(),
				)
			}
		}
	}
}

private val component = object : CreateNote {
	override val state: Value<CreateNote.State> = MutableValue(CreateNote.State())
	override val noteText: Value<String> = MutableValue(
		"A draft note about the lighthouse keeper and the night the lamp went dark."
	)

	override suspend fun createNote(noteText: String, tags: Set<String>): NoteError = NoteError.NONE
	override fun closeCreate() {}
	override fun confirmDiscard() {}
	override fun cancelDiscard() {}
	override fun onTextChanged(newText: String) {}
	override fun clearText() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
