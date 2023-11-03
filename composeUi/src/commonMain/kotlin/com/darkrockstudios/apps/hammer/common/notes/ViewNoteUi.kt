package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.notes.ViewNote
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ViewNoteUi(component: ViewNote, modifier: Modifier, rootSnackbar: RootSnackbarHostState) {
	val state by component.state.subscribeAsState()

	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val noteText by component.noteText.subscribeAsState()
	val annotatedNoteText = remember(noteText) {
		AnnotatedString.Builder(noteText).toAnnotatedString()
	}

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					MR.strings.notes_view_header.get(),
					style = MaterialTheme.typography.displaySmall
				)

				Row {
					IconButton(
						onClick = { component.confirmDelete() },
					) {
						Icon(Icons.Filled.Delete, MR.strings.notes_note_item_action_delete.get())
					}

					IconButton(onClick = {
						component.confirmClose()
					}) {
						Icon(
							Icons.Filled.Close,
							MR.strings.notes_note_item_action_cancel.get(),
						)
					}
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			val date = remember(state.note?.created) {
				state.note?.created?.toLocalDateTime(TimeZone.currentSystemDefault())
					?.format("dd MMM `yy")
			}

			Text(
				date ?: "",
				style = MaterialTheme.typography.bodySmall
			)

			Row(horizontalArrangement = Arrangement.Center) {
				if (state.isEditing) {
					Column(modifier = Modifier.weight(1f)) {
						Row {
							IconButton(onClick = {
								scope.launch {
									component.storeNoteUpdate()
									withContext(mainDispatcher) {
										component.discardEdit()
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
								component.confirmDiscard()
							}) {
								Icon(
									Icons.Filled.Cancel,
									MR.strings.notes_note_item_action_cancel.get(),
									tint = MaterialTheme.colorScheme.error
								)
							}
						}
						TextField(
							modifier = Modifier
								.fillMaxWidth()
								.widthIn(max = TextEditorDefaults.MAX_WIDTH)
								.fillMaxHeight(),
							value = noteText,
							onValueChange = { component.onContentChanged(it) },
						)
					}
				} else {
					ClickableText(
						annotatedNoteText,
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.bodyMedium
							.copy(color = MaterialTheme.colorScheme.onSurface),
					) {
						component.beginEdit()
					}
				}
			}
		}
	}

	if (state.confirmDiscard || state.confirmClose) {
		SimpleConfirm(
			title = MR.strings.notes_discard_dialog_title.get(),
			message = MR.strings.notes_discard_dialog_message.get(),
			onDismiss = {
				component.cancelDiscard()
				component.cancelClose()
			}
		) {
			component.discardEdit()

			if (state.confirmClose) {
				component.closeNote()
			}

			component.cancelDiscard()
			component.cancelClose()
		}
	}

	if (state.confirmDelete) {
		state.note?.let { note ->
			ConfirmDeleteNoteDialog(note, component, rootSnackbar, scope)
		}
	}
}