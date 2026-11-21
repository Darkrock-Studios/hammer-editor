package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.*
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
	val noteText by component.noteText.subscribeAsState()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()
	var newNoteError by remember { mutableStateOf(false) }
	var resetVersion by remember { mutableStateOf(0) }
	val tags = rememberSaveableStringList()

	val wordCount = remember(noteText) {
		countWords(noteText)
	}
	val charCount = noteText.length
	val canCreate = noteText.isNotBlank()

	val create: () -> Unit = {
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
	}

	BoxWithConstraints(
		modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = modifier
				.widthIn(max = Ui.MaxWidth.Modal)
				.fillMaxWidth()
				.heightIn(max = maxHeight)
				.background(MaterialTheme.colorScheme.surface, RectangleShape)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outline,
					shape = RectangleShape,
				),
		) {
			CollapseWhileTyping {
				Column {
					Text(
						text = Res.string.notes_create_header.get(),
						style = MaterialTheme.typography.titleLarge,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Ui.Padding.XL, bottom = Ui.Padding.L),
					)
					HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
				}
			}

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f, fill = false)
					.padding(horizontal = 28.dp, vertical = 22.dp),
				verticalArrangement = Arrangement.spacedBy(22.dp),
			) {
				Text(
					text = Res.string.notes_create_body_label.get(),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				key(resetVersion) {
					MarkdownEditField(
						initialMarkdown = noteText,
						onMarkdownChanged = { component.onTextChanged(it) },
						enableSpellChecking = state.spellCheckAllowed,
						autoFocus = true,
						contentPadding = PaddingValues(Ui.Padding.XL),
						testTag = NOTES_CREATE_BODY_TAG,
						modifier = Modifier
							.fillMaxWidth()
							.weight(1f, fill = false)
							.heightIn(min = 240.dp),
					)
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

			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(horizontal = 24.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				HdMonoLabel(
					text = Res.string.notes_view_status_word_char.get(wordCount, charCount),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.testTag(NOTES_CREATE_META_TAG),
				)
				Spacer(modifier = Modifier.weight(1f))
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
			}
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
