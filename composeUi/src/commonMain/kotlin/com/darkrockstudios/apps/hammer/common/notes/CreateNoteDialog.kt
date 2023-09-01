package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateNoteDialog(
	component: Notes,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
) {
	val strRes = rememberStrRes()

	SimpleDialog(
		visible = true,
		title = MR.strings.notes_create_header.get(),
		onCloseRequest = { component.dismissCreate() }
	) {
		val mainDispatcher = rememberMainDispatcher()
		var newNoteText by remember { mutableStateOf("") }
		var newNoteError by remember { mutableStateOf(false) }

		Box(
			modifier = Modifier.wrapContentWidth().heightIn(100.dp, 300.dp)
				.align(Alignment.CenterHorizontally)
		) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				Text(
					MR.strings.notes_create_body_hint.get(),
					style = MaterialTheme.typography.headlineLarge
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				OutlinedTextField(
					value = newNoteText,
					onValueChange = { newNoteText = it },
					modifier = Modifier.weight(1f),
					isError = newNoteError,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = {
						scope.launch {
							val result = component.createNote(newNoteText)
							newNoteError = !result.isSuccess
							when (result) {
								NoteError.TOO_LONG -> scope.launch {
									snackbarHostState.showSnackbar(strRes.get(MR.strings.notes_create_toast_too_long))
								}

								NoteError.EMPTY -> scope.launch {
									snackbarHostState.showSnackbar(strRes.get(MR.strings.notes_create_toast_empty))
								}

								NoteError.NONE -> {
									withContext(mainDispatcher) {
										newNoteText = ""
									}
									scope.launch {
										snackbarHostState.showSnackbar(strRes.get(MR.strings.notes_create_toast_success))
									}
								}
							}
						}
					}) {
						Text(MR.strings.notes_create_create_button.get())
					}

					Spacer(modifier = Modifier.weight(1f))

					Button(onClick = { component.dismissCreate() }) {
						Text(MR.strings.notes_create_cancel_button.get())
					}
				}
			}
		}
	}
}