package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
	var newEntryNameText by rememberSaveable { mutableStateOf("") }
	var newEntryContentText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(TextFieldValue(""))
	}
	var newTagsText by rememberSaveable { mutableStateOf("") }
	var selectedType by rememberSaveable { mutableStateOf(EntryType.PERSON) }
	val types = rememberSaveable { EntryType.entries }

	var imagePath by remember { mutableStateOf<PlatformFile?>(null) }

	val filePickerLauncher = rememberFilePickerLauncher(
		type = FileKitType.Image
	) { file ->
		imagePath = file
	}

	BoxWithConstraints(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		Card(modifier = Modifier.heightIn(0.dp, maxHeight).verticalScroll(rememberScrollState())) {
			Column(
				modifier = modifier.padding(Ui.Padding.XL)
					.widthIn(128.dp, 420.dp)
			) {
				Text(
					Res.string.encyclopedia_create_entry_header.get(),
					modifier = Modifier.padding(PaddingValues(bottom = Ui.Padding.XL)),
					style = MaterialTheme.typography.displayMedium
				)

				Text(
					Res.string.encyclopedia_create_entry_type_label.get(),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.padding(bottom = Ui.Padding.M)
				)

				ExposedDropDown(
					modifier = Modifier.fillMaxWidth(),
					items = types,
					getText = { it.toStringResource().get() },
					selectedItem = selectedType,
				) { item ->
					if (item != null) {
						selectedType = item
					} else {
						Napier.w { "EntryType cannot be null" }
					}
				}

				TextField(
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(top = Ui.Padding.XL, bottom = Ui.Padding.L)),
					value = newEntryNameText,
					onValueChange = { newEntryNameText = it },
					placeholder = { Text(Res.string.encyclopedia_create_entry_name_label.get()) },
					singleLine = true
				)

				TextField(
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(bottom = Ui.Padding.L)),
					value = newTagsText,
					onValueChange = { newTagsText = it },
					placeholder = { Text(Res.string.encyclopedia_create_entry_tags_label.get()) },
					singleLine = true
				)

				OutlinedTextField(
					value = newEntryContentText,
					onValueChange = { newEntryContentText = it },
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(bottom = Ui.Padding.L)),
					placeholder = { Text(text = Res.string.encyclopedia_create_entry_body_hint.get()) },
					maxLines = 10,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Box(
					modifier = Modifier.fillMaxWidth()
						.border(width = 1.dp, color = MaterialTheme.colorScheme.outline),
					contentAlignment = Alignment.Center
				) {
					if (imagePath != null) {
						Box(
							modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
						) {
							AsyncImage(
								model = imagePath?.absolutePath(),
								contentDescription = null,
								modifier = Modifier.size(128.dp).background(Color.LightGray),
							)
							Button(
								modifier = Modifier
									.padding(PaddingValues(start = Ui.Padding.XL))
									.align(Alignment.TopEnd),
								onClick = { imagePath = null }
							) {
								Icon(
									Icons.Default.Delete,
									Res.string.encyclopedia_create_entry_remove_image_button.get()
								)
							}
						}
					} else {
						Button(onClick = { filePickerLauncher.launch() }) {
							Text(Res.string.encyclopedia_create_entry_select_image_button.get())
						}
					}
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(modifier = Modifier.fillMaxWidth()) {
					Button(
						modifier = Modifier.weight(1f).padding(PaddingValues(end = Ui.Padding.XL)),
						onClick = {
							scope.launch {
								val result = component.createEntry(
									name = newEntryNameText,
									type = selectedType,
									text = newEntryContentText.text,
									tags = newTagsText.splitToSequence(" ").toSet(),
									imagePath = imagePath?.absolutePath()
								)

								when (result.error) {
									EntryError.NAME_TOO_LONG -> scope.launch {
										rootSnackbar.showSnackbar(
											strRes.get(
												Res.string.encyclopedia_create_entry_toast_too_long,
												EncyclopediaRepository.MAX_NAME_SIZE
											)
										)
									}

									EntryError.NAME_INVALID_CHARACTERS -> scope.launch {
										rootSnackbar.showSnackbar(
											strRes.get(Res.string.encyclopedia_create_entry_toast_invalid_name)
										)
									}

									EntryError.TAG_TOO_LONG -> scope.launch {
										rootSnackbar.showSnackbar(
											strRes.get(
												Res.string.encyclopedia_create_entry_toast_tag_too_long,
												EncyclopediaRepository.MAX_TAG_SIZE
											)
										)
									}

									EntryError.NAME_TOO_SHORT -> scope.launch {
										rootSnackbar.showSnackbar(
											strRes.get(
												Res.string.encyclopedia_create_entry_toast_tag_too_short,
											)
										)
									}

									EntryError.NONE -> {
										newEntryNameText = ""
										close()
										scope.launch { rootSnackbar.showSnackbar(strRes.get(Res.string.encyclopedia_create_entry_toast_success)) }
									}
								}
							}
						}
					) {
						Text(Res.string.encyclopedia_create_entry_create_button.get())
					}

					Button(
						modifier = Modifier.weight(1f)
							.padding(PaddingValues(start = Ui.Padding.XL)),
						onClick = { component.confirmClose() }
					) {
						Text(Res.string.encyclopedia_create_entry_cancel_button.get())
					}
				}
			}
		}
	}

	if (state.showConfirmClose) {
		SimpleConfirm(
			title = Res.string.encyclopedia_create_entry_discard_title.get(),
			onDismiss = { component.dismissConfirmClose() }
		) {
			component.dismissConfirmClose()
			close()
		}
	}
}