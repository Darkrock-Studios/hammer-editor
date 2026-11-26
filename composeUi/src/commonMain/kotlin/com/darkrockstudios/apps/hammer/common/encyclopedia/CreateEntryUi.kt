package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.actions.RegisterActions
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCreateModal
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCreateModalDefaults
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineImageDrop
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTypePicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberCreateAction
import com.darkrockstudios.apps.hammer.common.compose.images.StagedImage
import com.darkrockstudios.apps.hammer.common.compose.images.imageAddResultMessage
import com.darkrockstudios.apps.hammer.common.compose.images.pickImageFile
import com.darkrockstudios.apps.hammer.common.compose.images.stageImage
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberSaveableStringList
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_cancel_button
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_cover_art_label
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_create_button
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_description_label
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_title
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_keep
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_message
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_header
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_image_attached
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_image_browse_button
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_image_drop_hint
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_image_replace
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_name_label
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_name_placeholder
import com.darkrockstudios.apps.hammer.encyclopedia_entry_dictionary_toggle_hint
import com.darkrockstudios.apps.hammer.encyclopedia_entry_dictionary_toggle_label
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_tags_hint
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_tags_label
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_tags_placeholder
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_alias_too_long
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_invalid_name
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_name_too_short
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_success
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_tag_too_long
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_too_long
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFieldLabel

const val ENCYCLOPEDIA_CREATE_NAME_TAG = "encyclopedia-create-name"
const val ENCYCLOPEDIA_CREATE_TAGS_TAG = "encyclopedia-create-tags"
const val ENCYCLOPEDIA_CREATE_CONFIRM_TAG = "encyclopedia-create-confirm"
fun encyclopediaTypeCellTag(type: EntryType) = "encyclopedia-type-${type.name}"

@Composable
internal fun CreateEntryUi(
	component: CreateEntry,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier,
	close: () -> Unit
) {
	val state by component.state.subscribeAsState()
	val strRes = rememberStrRes()
	val dispatcherIo = rememberIoDispatcher()

	var name by rememberSaveable { mutableStateOf("") }
	var description by rememberSaveable { mutableStateOf("") }
	val tags = rememberSaveableStringList()
	var selectedType by rememberSaveable { mutableStateOf(EntryType.PERSON) }
	var excludeFromDictionary by rememberSaveable { mutableStateOf(false) }

	var imagePath by remember { mutableStateOf<String?>(null) }

	// The draft lives here rather than in the component, so the close chord is registered here too.
	val requestClose: () -> Unit = {
		val isDirty = name.isNotBlank() || description.isNotBlank() || tags.isNotEmpty() || imagePath != null
		if (isDirty) component.confirmClose() else close()
	}
	RegisterActions(ActionLayer.Item, mapOf(HammerActions.ItemClose.id to ActionHandler { requestClose() }))

	suspend fun reportImage(result: ImageAddResult) {
		imageAddResultMessage(result, strRes)?.let { rootSnackbar.showSnackbar(it) }
	}

	suspend fun applyImage(picked: PlatformFile) {
		when (val staged = picked.stageImage(dispatcherIo)) {
			is StagedImage.Staged -> imagePath = staged.path
			is StagedImage.Failed -> reportImage(staged.result)
		}
	}

	val create = rememberCreateAction(scope) {
		val result = component.createEntry(
			name = name,
			type = selectedType,
			text = description,
			tags = tags.toSet(),
			imagePath = imagePath,
			excludeFromDictionary = excludeFromDictionary,
		)
		val message = when (result.error) {
			EntryError.NAME_TOO_LONG -> strRes.get(
				Res.string.encyclopedia_create_entry_toast_too_long,
				EncyclopediaRepository.MAX_NAME_SIZE,
			)

			EntryError.NAME_INVALID_CHARACTERS -> strRes.get(
				Res.string.encyclopedia_create_entry_toast_invalid_name
			)

			EntryError.TAG_TOO_LONG -> strRes.get(
				Res.string.encyclopedia_create_entry_toast_tag_too_long,
				EncyclopediaRepository.MAX_TAG_SIZE,
			)

			EntryError.NAME_TOO_SHORT -> strRes.get(
				Res.string.encyclopedia_create_entry_toast_name_too_short
			)

			EntryError.ALIAS_TOO_LONG -> strRes.get(
				Res.string.encyclopedia_create_entry_toast_alias_too_long,
				EncyclopediaRepository.MAX_NAME_SIZE,
			)

			EntryError.NONE -> {
				name = ""
				description = ""
				tags.clear()
				imagePath = null
				close()
				strRes.get(Res.string.encyclopedia_create_entry_toast_success)
			}
		}
		// Outside the create, so the next one need not wait for the snackbar.
		scope.launch { rootSnackbar.showSnackbar(message) }
	}

	HdCreateModal(
		title = Res.string.encyclopedia_create_entry_header.get(),
		modifier = modifier,
		actions = {
			HdHairlineButton(
				label = Res.string.encyclopedia_create_entry_cancel_button.get(),
				onClick = { component.confirmClose() },
			)
			HdHairlineButton(
				label = Res.string.encyclopedia_create_entry_create_button.get(),
				onClick = create,
				emphasised = true,
				modifier = Modifier.testTag(ENCYCLOPEDIA_CREATE_CONFIRM_TAG),
			)
		},
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f, fill = false)
				.verticalScroll(rememberScrollState())
				.padding(HdCreateModalDefaults.BodyPadding),
			verticalArrangement = Arrangement.spacedBy(HdCreateModalDefaults.BodySpacing),
		) {
			HdHairlineField(
				label = Res.string.encyclopedia_create_entry_name_label.get(),
				value = name,
				onValueChange = { name = it.take(EncyclopediaRepository.MAX_NAME_SIZE) },
				placeholder = Res.string.encyclopedia_create_entry_name_placeholder.get(),
				counter = "${name.length}/${EncyclopediaRepository.MAX_NAME_SIZE}",
				autoFocus = true,
				testTag = ENCYCLOPEDIA_CREATE_NAME_TAG,
			)

			HdHairlineTypePicker(
				selected = selectedType,
				onSelect = { selectedType = it },
				cellTestTag = ::encyclopediaTypeCellTag,
			)

			HdHairlineTagField(
				label = Res.string.encyclopedia_create_entry_tags_label.get(),
				tags = tags,
				onTagsChange = {
					tags.clear()
					tags.addAll(it)
				},
				hint = Res.string.encyclopedia_create_entry_tags_hint.get(),
				placeholder = Res.string.encyclopedia_create_entry_tags_placeholder.get(),
				suggestTags = component::suggestTags,
				testTag = ENCYCLOPEDIA_CREATE_TAGS_TAG,
			)

			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				HdFieldLabel(text = Res.string.encyclopedia_create_entry_description_label.get())
				MarkdownEditField(
					initialMarkdown = remember { description },
					onMarkdownChanged = { description = it },
					enableSpellChecking = state.spellCheckAllowed,
					contentPadding = PaddingValues(Ui.Padding.XL),
					modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
				)
			}

			val attachedImage = imagePath
			HdHairlineImageDrop(
				label = Res.string.encyclopedia_create_entry_cover_art_label.get(),
				onClick = {
					scope.launch {
						pickImageFile(::reportImage)?.let { applyImage(it) }
					}
				},
				onFilesDropped = { files ->
					files.firstOrNull()?.let { file ->
						scope.launch { applyImage(file) }
					}
				},
				dropExtensions = ImageFormat.ALLOWED_EXTENSIONS,
				dropHint = Res.string.encyclopedia_create_entry_image_drop_hint.get(),
				browseLabel = Res.string.encyclopedia_create_entry_image_browse_button.get(),
				attachedLabel = Res.string.encyclopedia_create_entry_image_attached.get(),
				replaceLabel = Res.string.encyclopedia_create_entry_image_replace.get(),
				image = if (attachedImage != null) {
					{
						AsyncImage(
							model = attachedImage,
							contentDescription = null,
							modifier = Modifier.fillMaxSize(),
							contentScale = ContentScale.Crop,
						)
					}
				} else null,
				onRemove = if (attachedImage != null) {
					{ imagePath = null }
				} else null,
			)

			if (state.dictionaryFeatureEnabled) {
				HdHairlineToggleRow(
					checked = !excludeFromDictionary,
					label = Res.string.encyclopedia_entry_dictionary_toggle_label.get(),
					hint = Res.string.encyclopedia_entry_dictionary_toggle_hint.get(),
					onCheckedChange = { include -> excludeFromDictionary = !include },
				)
			}
		}
	}

	if (state.showConfirmClose) {
		SimpleConfirm(
			title = Res.string.encyclopedia_create_entry_discard_title.get(),
			message = Res.string.encyclopedia_create_entry_discard_message.get(),
			positiveButton = Res.string.encyclopedia_create_entry_discard_confirm.get(),
			negativeButton = Res.string.encyclopedia_create_entry_discard_keep.get(),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
			onDismiss = { component.dismissConfirmClose() },
		) {
			component.dismissConfirmClose()
			close()
		}
	}
}
