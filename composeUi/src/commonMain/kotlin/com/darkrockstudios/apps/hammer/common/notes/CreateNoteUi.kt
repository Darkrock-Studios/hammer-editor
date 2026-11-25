package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCreateModal
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCreateModalDefaults
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.rememberSaveableStringList
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFieldLabel

const val NOTES_CREATE_BODY_TAG = "notes-create-body"
const val NOTES_CREATE_CONFIRM_TAG = "notes-create-confirm"
const val NOTES_CREATE_CANCEL_TAG = "notes-create-cancel"
const val NOTES_CREATE_META_TAG = "notes-create-meta"

@Composable
fun CreateNoteUi(
	component: CreateNote,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState
) {
	val state by component.state.subscribeAsState()
	val noteText = component.noteText.subscribeAsState()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()
	var newNoteError by remember { mutableStateOf(false) }
	var resetVersion by remember { mutableStateOf(0) }
	val tags = rememberSaveableStringList()

	val canCreate by remember { derivedStateOf { noteText.value.isNotBlank() } }

	val create: () -> Unit = {
		scope.launch {
			val result = component.createNote(noteText.value, tags.toSet())
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
	}

	HdCreateModal(
		title = Res.string.notes_create_header.get(),
		modifier = modifier,
		footerMeta = {
			val text = noteText.value
			val wordCount = remember(text) { countWords(text) }
			HdMonoLabel(
				text = Res.string.notes_view_status_word_char.get(wordCount, text.length),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.testTag(NOTES_CREATE_META_TAG),
			)
		},
		actions = {
			HdHairlineButton(
				label = Res.string.notes_create_cancel_button.get(),
				onClick = { component.closeCreate() },
				modifier = Modifier.testTag(NOTES_CREATE_CANCEL_TAG),
			)
			HdHairlineButton(
				label = Res.string.notes_create_create_button.get(),
				emphasised = true,
				enabled = canCreate,
				modifier = Modifier.testTag(NOTES_CREATE_CONFIRM_TAG),
				onClick = create,
			)
		},
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f, fill = false)
				.padding(HdCreateModalDefaults.BodyPadding),
			verticalArrangement = Arrangement.spacedBy(HdCreateModalDefaults.BodySpacing),
		) {
			Column(
				modifier = Modifier.weight(1f, fill = false),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdFieldLabel(text = Res.string.notes_create_body_label.get())
				key(resetVersion) {
					MarkdownEditField(
						initialMarkdown = remember { noteText.value },
						onMarkdownChanged = { component.onTextChanged(it) },
						enableSpellChecking = state.spellCheckAllowed,
						imagePicker = component::pickImageForText,
						autoFocus = true,
						contentPadding = PaddingValues(Ui.Padding.XL),
						testTag = NOTES_CREATE_BODY_TAG,
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f, fill = false)
							.heightIn(min = 240.dp),
					)
				}
			}

			HdHairlineTagField(
				label = Res.string.notes_create_tags_label.get(),
				tags = tags,
				onTagsChange = {
					tags.clear()
					tags.addAll(it)
				},
				hint = Res.string.notes_create_tags_hint.get(),
				placeholder = Res.string.notes_create_tags_placeholder.get(),
				suggestTags = component::suggestTags,
			)
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.notes_discard_dialog_title.get(),
			message = Res.string.notes_discard_dialog_message.get(),
			positiveButton = Res.string.notes_discard_confirm.get(),
			negativeButton = Res.string.notes_discard_keep.get(),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
			onDismiss = {
				component.cancelDiscard()
			}
		) {
			component.clearText()
			component.closeCreate()
		}
	}
}
