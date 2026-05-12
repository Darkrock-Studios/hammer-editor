package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.notes.ViewNote
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val ModalMaxWidth = TextEditorDefaults.MAX_WIDTH * 1.25f
private val ModalMaxHeight = 760.dp

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewNoteUi(
	component: ViewNote,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()

	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val noteText by component.noteText.subscribeAsState()
	val isEditing = state.isEditing

	val saveChanges: () -> Unit = {
		scope.launch {
			component.storeNoteUpdate()
			withContext(mainDispatcher) {
				component.discardEdit()
			}
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surfaceDim),
		contentAlignment = Alignment.TopCenter,
	) {
		with(sharedTransitionScope) {
			Column(
				modifier = Modifier
					.padding(Ui.Padding.XL)
					.widthIn(max = ModalMaxWidth)
					.heightIn(max = ModalMaxHeight)
					.fillMaxWidth()
					.then(if (isEditing) Modifier.fillMaxHeight() else Modifier)
					.background(MaterialTheme.colorScheme.surface)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.sharedElement(
						sharedContentState = rememberSharedContentState(
							key = "note-card-${state.note?.id}",
						),
						animatedVisibilityScope = animatedVisibilityScope,
					),
			) {
				CrumbRow(
					onClose = { component.confirmClose() },
					menuSlot = { DetailViewDropdownMenu(menuItems = state.menuItems) },
				)

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				StampRow(
					isEditing = isEditing,
					note = state.note,
					sharedTransitionScope = sharedTransitionScope,
					animatedVisibilityScope = animatedVisibilityScope,
					onEdit = { component.beginEdit() },
					onSave = saveChanges,
					onCancel = { component.confirmDiscard() },
				)

				HorizontalDivider(
					thickness = 2.dp,
					color = MaterialTheme.colorScheme.outline,
				)

				if (isEditing) {
					EditBody(
						tags = state.tags.toList(),
						onTagsChanged = { component.onTagsChanged(it.toSet()) },
						noteText = noteText,
						onNoteTextChanged = { component.onContentChanged(it) },
						suggestTags = component::suggestTags,
						modifier = Modifier.weight(1f),
					)

					EditStatusFooter(text = noteText)
				} else {
					ViewBody(
						note = state.note,
						noteText = noteText,
						onEnterEdit = { component.beginEdit() },
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
						modifier = Modifier.weight(1f, fill = false),
					)

					ViewFolioFooter(note = state.note, noteText = noteText)
				}
			}
		}
	}

	if (state.confirmDiscard || state.confirmClose) {
		SimpleConfirm(
			title = Res.string.notes_discard_dialog_title.get(),
			message = Res.string.notes_discard_dialog_message.get(),
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

@Composable
private fun CrumbRow(
	onClose: () -> Unit,
	menuSlot: @Composable () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Row(
			modifier = Modifier
				.clickable(
					onClick = onClose,
					role = Role.Button,
					onClickLabel = Res.string.notes_view_close_button.get(),
				)
				.padding(vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = "← " + Res.string.notes_view_crumb_root.get(),
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		Spacer(modifier = Modifier.weight(1f))
		menuSlot()
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StampRow(
	isEditing: Boolean,
	note: NoteContent?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
) {
	val sectionTitle = if (isEditing) {
		Res.string.notes_view_label_editing.get()
	} else {
		Res.string.notes_view_header.get()
	}
	val date = remember(note?.created) {
		note?.created?.toLocalDateTime(TimeZone.currentSystemDefault())
			?.format("dd MMM yyyy")
			.orEmpty()
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = "§ III · $sectionTitle",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		if (isEditing) {
			PulsingDot()
		}

		Box(
			modifier = Modifier
				.height(14.dp)
				.width(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)

		val metaText = if (isEditing) {
			Res.string.notes_view_status_unsaved.get()
		} else {
			date
		}
		with(sharedTransitionScope) {
			HdMonoLabel(
				text = metaText,
				modifier = Modifier.sharedElement(
					sharedContentState = rememberSharedContentState(
						key = "note-date-${note?.id}",
					),
					animatedVisibilityScope = animatedVisibilityScope,
				),
			)
		}

		Spacer(modifier = Modifier.weight(1f))

		if (isEditing) {
			HdHairlineButton(
				label = Res.string.notes_view_action_save.get(),
				onClick = onSave,
				emphasised = true,
			)
			HdHairlineButton(
				label = Res.string.notes_note_item_action_cancel.get(),
				onClick = onCancel,
			)
		} else {
			HdHairlineButton(
				label = Res.string.notes_view_action_edit.get(),
				onClick = onEdit,
			)
		}
	}
}

@Composable
private fun PulsingDot() {
	val transition = rememberInfiniteTransition(label = "noteEditingPulse")
	val alpha by transition.animateFloat(
		initialValue = 1f,
		targetValue = 0.35f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 800),
			repeatMode = RepeatMode.Reverse,
		),
		label = "noteEditingPulseAlpha",
	)
	Box(
		modifier = Modifier
			.size(7.dp)
			.alpha(alpha)
			.background(MaterialTheme.colorScheme.primary, RectangleShape),
	)
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ViewBody(
	note: NoteContent?,
	noteText: String,
	onEnterEdit: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
) {
	val scrollState = rememberScrollState()
	Column(
		modifier = modifier
			.fillMaxWidth()
			.verticalScroll(scrollState)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.XL),
	) {
		val noteTags = note?.tags
		if (!noteTags.isNullOrEmpty()) {
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				noteTags.sorted().forEach { tag ->
					HdTagChip(label = tag, active = true)
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				modifier = Modifier.padding(bottom = Ui.Padding.L),
			)
		}

		with(sharedTransitionScope) {
			MarkdownView(
				markdown = noteText,
				modifier = Modifier
					.fillMaxWidth()
					.sharedElement(
						sharedContentState = rememberSharedContentState(
							key = "note-content-${note?.id}",
						),
						animatedVisibilityScope = animatedVisibilityScope,
					)
					.clickable(onClick = onEnterEdit),
			)
		}
	}
}

@Composable
private fun EditBody(
	tags: List<String>,
	onTagsChanged: (List<String>) -> Unit,
	noteText: String,
	onNoteTextChanged: (String) -> Unit,
	suggestTags: (prefix: String) -> List<String>,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier.fillMaxWidth()) {
		HdHairlineTagField(
			label = Res.string.notes_create_tags_label.get(),
			tags = tags,
			onTagsChange = onTagsChanged,
			hint = Res.string.notes_create_tags_hint.get(),
			placeholder = Res.string.notes_create_tags_placeholder.get(),
			suggestTags = suggestTags,
			modifier = Modifier.padding(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
		)

		HorizontalDivider(
			thickness = 2.dp,
			color = MaterialTheme.colorScheme.outline,
		)

		MarkdownEditField(
			initialMarkdown = noteText,
			onMarkdownChanged = onNoteTextChanged,
			contentPadding = PaddingValues(Ui.Padding.XL),
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
		)
	}
}

@Composable
private fun ViewFolioFooter(note: NoteContent?, noteText: String) {
	val words = remember(noteText) {
		noteText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
	}
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		note?.id?.let { id ->
			HdEntityId(prefix = "NOTE", id = id)
		}
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = Res.string.notes_word_count_short.get(words),
		)
		val tagCount = note?.tags?.size ?: 0
		if (tagCount > 0) {
			Box(
				modifier = Modifier
					.height(14.dp)
					.width(Dp.Hairline)
					.background(MaterialTheme.colorScheme.outlineVariant),
			)
			HdMonoLabel(text = "$tagCount TAGS")
		}
	}
}

@Composable
private fun EditStatusFooter(text: String) {
	val words = remember(text) {
		text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
	}
	val chars = text.length

	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(text = Res.string.notes_view_status_word_char.get(words, chars))
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(text = Res.string.notes_view_status_hint.get())
	}
}
