package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

	var name by rememberSaveable { mutableStateOf("") }
	var description by rememberSaveable { mutableStateOf("") }
	val tags = remember { mutableStateListOf<String>() }
	var selectedType by rememberSaveable { mutableStateOf(EntryType.PERSON) }

	var imagePath by remember { mutableStateOf<PlatformFile?>(null) }

	val filePickerLauncher = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
		imagePath = file
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
				HairlineModalHeader(
					marker = Res.string.encyclopedia_create_entry_section_marker.get(),
					title = Res.string.encyclopedia_create_entry_header.get(),
					meta = Res.string.encyclopedia_create_entry_draft_marker.get(),
				)
			}

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 28.dp, vertical = 22.dp),
				verticalArrangement = Arrangement.spacedBy(22.dp),
			) {
				HdHairlineField(
					label = Res.string.encyclopedia_create_entry_name_label.get(),
					value = name,
					onValueChange = { name = it.take(EncyclopediaRepository.MAX_NAME_SIZE) },
					placeholder = Res.string.encyclopedia_create_entry_name_placeholder.get(),
					counter = "${name.length}/${EncyclopediaRepository.MAX_NAME_SIZE}",
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

				Text(
					text = Res.string.encyclopedia_create_entry_description_label.get(),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				MarkdownEditField(
					initialMarkdown = description,
					onMarkdownChanged = { description = it },
					contentPadding = PaddingValues(Ui.Padding.XL),
					modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
				)

				val attachedImage = imagePath
				HdHairlineImageDrop(
					label = Res.string.encyclopedia_create_entry_cover_art_label.get(),
					onClick = { filePickerLauncher.launch() },
					dropHint = Res.string.encyclopedia_create_entry_image_drop_hint.get(),
					browseLabel = Res.string.encyclopedia_create_entry_image_browse_button.get(),
					attachedLabel = Res.string.encyclopedia_create_entry_image_attached.get(),
					replaceLabel = Res.string.encyclopedia_create_entry_image_replace.get(),
					image = if (attachedImage != null) {
						{
							AsyncImage(
								model = attachedImage.absolutePath(),
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
			}

			HairlineModalFooter(
				type = selectedType,
				onCancel = { component.confirmClose() },
				onCreate = {
					scope.launch {
						val result = component.createEntry(
							name = name,
							type = selectedType,
							text = description,
							tags = tags.toSet(),
							imagePath = imagePath?.absolutePath(),
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
								Res.string.encyclopedia_create_entry_toast_tag_too_short
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
						rootSnackbar.showSnackbar(message)
					}
				},
			)
		}
	}

	if (state.showConfirmClose) {
		SimpleConfirm(
			title = Res.string.encyclopedia_create_entry_discard_title.get(),
			onDismiss = { component.dismissConfirmClose() },
		) {
			component.dismissConfirmClose()
			close()
		}
	}
}

@Composable
private fun HairlineModalHeader(
	marker: String,
	title: String,
	meta: String,
) {
	Column {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(Ui.TOP_BAR_HEIGHT)
				.padding(horizontal = Ui.Padding.XL),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			HdMonoLabel(text = "§ $marker")
			Text(
				text = title,
				style = MaterialTheme.typography.headlineMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
			HdMonoLabel(
				text = meta,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@Composable
private fun HairlineModalFooter(
	type: EntryType,
	onCancel: () -> Unit,
	onCreate: () -> Unit,
) {
	val typeColor = LocalHammerColors.current.colorFor(type)
	Column {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surfaceContainerLow)
				.padding(horizontal = 24.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Row(
				modifier = Modifier
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.padding(horizontal = 8.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Box(
					modifier = Modifier
						.size(10.dp)
						.background(typeColor, RectangleShape),
				)
				HdMonoLabel(
					text = type.toStringResource().get(),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdMonoLabel(
					text = type.glyph(),
					color = typeColor,
				)
			}
			Box(modifier = Modifier.weight(1f))
			HdHairlineButton(
				label = Res.string.encyclopedia_create_entry_cancel_button.get(),
				onClick = onCancel,
			)
			HdHairlineButton(
				label = Res.string.encyclopedia_create_entry_create_button.get(),
				onClick = onCreate,
				emphasised = true,
				modifier = Modifier.testTag(ENCYCLOPEDIA_CREATE_CONFIRM_TAG),
			)
		}
	}
}
