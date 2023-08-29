package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToAnnotatedString
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun NotesUi(
	component: Notes,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	val snackbarHostState = remember { SnackbarHostState() }

	Box(modifier = modifier.padding(horizontal = Ui.Padding.XL)) {
		Column {
			Text(
				MR.strings.notes_header.get(),
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground
			)

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Adaptive(400.dp),
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(horizontal = Ui.Padding.XL)
			) {
				if (state.notes.isEmpty()) {
					item {
						Text(
							MR.strings.notes_list_empty.get(),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onBackground
						)
					}
				}

				items(
					count = state.notes.size,
				) { index ->
					NoteItem(
						note = state.notes[index],
						component = component,
					)
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}

	if (state.showCreate) {
		CreateNoteDialog(component, snackbarHostState, scope)
	}

	state.confirmDelete?.let { note ->
		ConfirmDeleteDialog(note, component, snackbarHostState, scope)
	}
}

@Composable
fun NoteItem(
	note: NoteContent,
	component: Notes,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	var isEditing by rememberSaveable { mutableStateOf(false) }
	var discardConfirm by rememberSaveable { mutableStateOf(false) }
	var updatedNoteText by rememberSaveable(isEditing) { mutableStateOf(note.content) }

	Card(
		modifier = modifier.fillMaxWidth().padding(Ui.Padding.XL),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()
		) {
			Row {
				if (isEditing) {
					Column(modifier = Modifier.weight(1f)) {
						Row {
							IconButton(onClick = {
								scope.launch {
									component.updateNote(note.copy(content = updatedNoteText))
									withContext(mainDispatcher) {
										isEditing = false
									}
								}
							}) {
								Icon(
									Icons.Filled.Check,
									MR.strings.notes_note_item_action_rename.get(),
									tint = MaterialTheme.colorScheme.onSurface
								)
							}
							IconButton(onClick = {
								discardConfirm = true
							}) {
								Icon(
									Icons.Filled.Cancel,
									MR.strings.notes_note_item_action_cancel.get(),
									tint = MaterialTheme.colorScheme.error
								)
							}
						}
						TextField(
							modifier = Modifier.fillMaxWidth(),
							value = updatedNoteText,
							onValueChange = { updatedNoteText = it },
						)
					}
				} else {
					ClickableText(
						note.content.markdownToAnnotatedString(),
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.bodyMedium
							.copy(color = MaterialTheme.colorScheme.onBackground),
					) {
						isEditing = true
					}
				}

				IconButton(
					onClick = { component.confirmDelete(note) },
				) {
					Icon(Icons.Filled.Delete, MR.strings.notes_note_item_action_delete.get())
				}
			}
			Spacer(modifier = Modifier.size(Ui.Padding.L))

			val date = remember(note.created) {
				val created = note.created.toLocalDateTime(TimeZone.currentSystemDefault())
				created.format("dd MMM `yy")
			}

			Text(
				date,
				style = MaterialTheme.typography.bodySmall
			)
		}
	}

	if (discardConfirm) {
		SimpleConfirm(
			title = MR.strings.notes_discard_dialog_title.get(),
			message = MR.strings.notes_discard_dialog_message.get(),
			onDismiss = { discardConfirm = false }
		) {
			isEditing = false
			discardConfirm = false
		}
	}
}

@Composable
fun NotesFab(
	component: Notes,
) {
	FloatingActionButton(
		onClick = { component.showCreate() },
	) {
		Icon(Icons.Filled.Create, MR.strings.notes_create_note_button.get())
	}
}