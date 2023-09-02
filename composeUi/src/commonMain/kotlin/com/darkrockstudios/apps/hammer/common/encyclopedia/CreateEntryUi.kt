package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.getHomeDirectory
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import com.darkrockstudios.libraries.mpfilepicker.MPFile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateEntryUi(
	component: CreateEntry,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState,
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
	val types = rememberSaveable { EntryType.values().toList() }

	var showFilePicker by rememberSaveable { mutableStateOf(false) }
	var imagePath by rememberSaveable { mutableStateOf<MPFile<Any>?>(null) }

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
					MR.strings.encyclopedia_create_entry_header.get(),
					modifier = Modifier.padding(PaddingValues(bottom = Ui.Padding.XL)),
					style = MaterialTheme.typography.displayMedium
				)

				Text(
					MR.strings.encyclopedia_create_entry_type_label.get(),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.padding(bottom = Ui.Padding.M)
				)

				ExposedDropDown(
					modifier = Modifier.fillMaxWidth(),
					padding = Ui.Padding.XL,
					items = types,
					getText = { strRes.get(it.toStringResource()) },
					defaultIndex = types.indexOf(EntryType.PERSON)
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
					placeholder = { Text(MR.strings.encyclopedia_create_entry_name_label.get()) }
				)

				TextField(
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(bottom = Ui.Padding.L)),
					value = newTagsText,
					onValueChange = { newTagsText = it },
					placeholder = { Text(MR.strings.encyclopedia_create_entry_tags_label.get()) }
				)

				OutlinedTextField(
					value = newEntryContentText,
					onValueChange = { newEntryContentText = it },
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(bottom = Ui.Padding.L)),
					placeholder = { Text(text = MR.strings.encyclopedia_create_entry_body_hint.get()) },
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
							ImageItem(
								modifier = Modifier.size(128.dp).background(Color.LightGray),
								path = imagePath?.path
							)
							Button(
								modifier = Modifier
									.padding(PaddingValues(start = Ui.Padding.XL))
									.align(Alignment.TopEnd),
								onClick = { imagePath = null }
							) {
								Icon(
									Icons.Default.Delete,
									MR.strings.encyclopedia_create_entry_remove_image_button.get()
								)
							}
						}
					} else {
						Button(onClick = { showFilePicker = true }) {
							Text(MR.strings.encyclopedia_create_entry_select_image_button.get())
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
									tags = newTagsText.splitToSequence(" ").toList(),
									imagePath = imagePath?.path
								)

								when (result.error) {
									EntryError.NAME_TOO_LONG -> scope.launch {
										snackbarHostState.showSnackbar(
											strRes.get(
												MR.strings.encyclopedia_create_entry_toast_too_long,
												EncyclopediaRepository.MAX_NAME_SIZE
											)
										)
									}

									EntryError.NAME_INVALID_CHARACTERS -> scope.launch {
										snackbarHostState.showSnackbar(
											strRes.get(MR.strings.encyclopedia_create_entry_toast_invalid_name)
										)
									}

									EntryError.TAG_TOO_LONG -> scope.launch {
										snackbarHostState.showSnackbar(
											strRes.get(
												MR.strings.encyclopedia_create_entry_toast_tag_too_long,
												EncyclopediaRepository.MAX_TAG_SIZE
											)
										)
									}

									EntryError.NAME_TOO_SHORT -> scope.launch {
										snackbarHostState.showSnackbar(
											strRes.get(
												MR.strings.encyclopedia_create_entry_toast_tag_too_short,
											)
										)
									}

									EntryError.NONE -> {
										newEntryNameText = ""
										close()
										scope.launch { snackbarHostState.showSnackbar(strRes.get(MR.strings.encyclopedia_create_entry_toast_success)) }
									}
								}
							}
						}
					) {
						Text(MR.strings.encyclopedia_create_entry_create_button.get())
					}

					Button(
						modifier = Modifier.weight(1f)
							.padding(PaddingValues(start = Ui.Padding.XL)),
						onClick = { component.confirmClose() }
					) {
						Text(MR.strings.encyclopedia_create_entry_cancel_button.get())
					}
				}
			}
		}
	}

	FilePicker(
		show = showFilePicker,
		fileExtensions = listOf("jpg"),
		initialDirectory = getHomeDirectory()
	) { path ->
		imagePath = path
		showFilePicker = false
	}

	if (state.showConfirmClose) {
		SimpleConfirm(
			title = MR.strings.encyclopedia_create_entry_discard_title.get(),
			onDismiss = { component.dismissConfirmClose() }
		) {
			component.dismissConfirmClose()
			close()
		}
	}
}