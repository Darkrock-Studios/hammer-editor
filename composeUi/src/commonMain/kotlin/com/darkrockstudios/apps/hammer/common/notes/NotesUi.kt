package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToAnnotatedString
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesUi(
	component: Notes
) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	val snackbarHostState = remember { SnackbarHostState() }

	Box(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
		Column {
			Text(
				"Notes",
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground
			)

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Adaptive(400.dp),
				modifier = Modifier.fillMaxWidth(),
				contentPadding = PaddingValues(Ui.Padding.XL)
			) {
				if (state.notes.isEmpty()) {
					item {
						Text(
							"No Notes Found",
							style = MaterialTheme.typography.displayMedium
						)
					}
				}

				items(
					count = state.notes.size,
				) { index ->
					NoteItem(
						note = state.notes[index],
						component = component,
						snackbarHostState = snackbarHostState,
						scope = scope,
					)
				}
			}
		}

		FloatingActionButton(
			onClick = { component.showCreate() },
			modifier = Modifier.align(Alignment.BottomEnd)
		) {
			Icon(Icons.Filled.Create, "Create Note")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(
	note: NoteContent,
	component: Notes,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	var isEditing by remember { mutableStateOf(false) }
	var updatedNoteText by remember { mutableStateOf(note.content) }

	Card(
		modifier = modifier.fillMaxWidth().padding(Ui.Padding.XL)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()
		) {
			Row {
				if (isEditing) {
					Column(modifier = Modifier.weight(1f)) {
						Row {
							IconButton(onClick = {
								component.updateNote(note.copy(content = updatedNoteText))
								isEditing = false
							}) {
								Icon(
									Icons.Filled.Check,
									"Rename",
									tint = MaterialTheme.colorScheme.onSurface
								)
							}
							IconButton(onClick = { isEditing = false }) {
								Icon(
									Icons.Filled.Cancel,
									"Cancel",
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
					Icon(Icons.Filled.Delete, "Delete")
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
}