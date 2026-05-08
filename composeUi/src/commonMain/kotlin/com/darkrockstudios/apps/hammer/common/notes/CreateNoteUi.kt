package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CreateNoteUi(
	component: CreateNote,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState
) {
	val state by component.state.subscribeAsState()
	val noteText by component.noteText.subscribeAsState()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()
	var newNoteError by remember { mutableStateOf(false) }
	var resetVersion by remember { mutableStateOf(0) }
	val tags = remember { mutableStateListOf<String>() }

	val wordCount = remember(noteText) {
		noteText.split(Regex("\\s+")).count { it.isNotBlank() }
	}
	val charCount = noteText.length
	val canCreate = noteText.isNotBlank()

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL),
		shape = RectangleShape,
	) {
		Column(modifier = Modifier.fillMaxWidth()) {

			// Masthead — § III · NEW marker, screen title, draft state.
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.XL,
						bottom = Ui.Padding.L,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdSectionHeader(
					marker = "III · NEW",
					title = Res.string.notes_create_header.get(),
					trailing = {
						HdMonoLabel(text = "DRAFT")
					},
				)
				HdMonoLabel(
					text = Res.string.notes_create_body_hint.get(),
					color = if (newNoteError) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			) {
				HdHairlineTagField(
					label = Res.string.notes_create_tags_label.get(),
					tags = tags,
					onTagsChange = {
						tags.clear()
						tags.addAll(it)
					},
					hint = Res.string.notes_create_tags_hint.get(),
					placeholder = Res.string.notes_create_tags_placeholder.get(),
				)
			}

			// Body editor framed in a hairline so it reads as its own block.
			Box(
				modifier = Modifier
					.padding(horizontal = Ui.Padding.XL)
					.padding(bottom = Ui.Padding.L)
					.fillMaxWidth()
					.weight(1f, fill = true)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					),
			) {
				key(resetVersion) {
					MarkdownEditField(
						initialMarkdown = noteText,
						onMarkdownChanged = { component.onTextChanged(it) },
						contentPadding = PaddingValues(Ui.Padding.XL),
						modifier = Modifier
							.fillMaxWidth()
							.widthIn(max = TextEditorDefaults.MAX_WIDTH),
					)
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			// Footer — word/char meta, keyboard hint, actions.
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				verticalAlignment = Alignment.CenterVertically,
			) {
				HdMonoLabel(text = "$wordCount W · $charCount CH")
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(
					label = Res.string.notes_create_cancel_button.get(),
					onClick = { component.closeCreate() },
				)
				HdHairlineButton(
					label = Res.string.notes_create_create_button.get(),
					emphasised = canCreate,
					onClick = {
						scope.launch {
							val result = component.createNote(noteText, tags.toSet())
							newNoteError = !result.isSuccess
							when (result) {
								NoteError.TOO_LONG -> scope.launch {
									rootSnackbar.showSnackbar(strRes.get(Res.string.notes_create_toast_too_long))
								}

								NoteError.EMPTY -> scope.launch {
									rootSnackbar.showSnackbar(strRes.get(Res.string.notes_create_toast_empty))
								}

								NoteError.TAG_TOO_LONG -> scope.launch {
									rootSnackbar.showSnackbar(
										strRes.get(
											Res.string.notes_create_toast_tag_too_long,
											NotesRepository.MAX_TAG_SIZE,
										)
									)
								}

								NoteError.NONE -> {
									withContext(mainDispatcher) {
										component.clearText()
										tags.clear()
										resetVersion++
									}
									scope.launch {
										rootSnackbar.showSnackbar(strRes.get(Res.string.notes_create_toast_success))
									}
								}
							}
						}
					},
				)
			}
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.notes_discard_dialog_title.get(),
			message = Res.string.notes_discard_dialog_message.get(),
			onDismiss = {
				component.cancelDiscard()
			}
		) {
			component.clearText()
			component.closeCreate()
		}
	}
}
