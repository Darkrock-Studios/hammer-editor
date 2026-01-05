package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
		Card(
			elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL),
			modifier = Modifier.heightIn(0.dp, maxHeight).verticalScroll(rememberScrollState())
		) {
			Column(
				modifier = modifier.padding(Ui.Padding.XL)
					.widthIn(128.dp, 420.dp),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)
			) {
				Text(
					Res.string.encyclopedia_create_entry_header.get(),
					style = MaterialTheme.typography.headlineLarge
				)

				ExposedDropDown(
					modifier = Modifier.fillMaxWidth(),
					items = types,
					label = Res.string.encyclopedia_create_entry_type_label.get(),
					getText = { it.toStringResource().get() },
					expandWidth = true,
					selectedItem = selectedType,
				) { item ->
					if (item != null) {
						selectedType = item
					} else {
						Napier.w { "EntryType cannot be null" }
					}
				}

				OutlinedTextField(
					modifier = Modifier.fillMaxWidth(),
					value = newEntryNameText,
					onValueChange = { newEntryNameText = it },
					label = { Text(Res.string.encyclopedia_create_entry_name_label.get()) },
					singleLine = true
				)

				OutlinedTextField(
					modifier = Modifier.fillMaxWidth(),
					value = newTagsText,
					onValueChange = { newTagsText = it },
					label = { Text(Res.string.encyclopedia_create_entry_tags_label.get()) },
					singleLine = true
				)

				OutlinedTextField(
					value = newEntryContentText,
					onValueChange = { newEntryContentText = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(text = Res.string.encyclopedia_create_entry_body_hint.get()) },
					maxLines = 10,
				)

				Surface(
					modifier = Modifier.fillMaxWidth().height(160.dp),
					shape = RoundedCornerShape(12.dp),
					color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
				) {
					Box(
						modifier = Modifier.fillMaxSize().padding(Ui.Padding.M),
						contentAlignment = Alignment.Center
					) {
						if (imagePath != null) {
							Box(
								modifier = Modifier.fillMaxSize(),
								contentAlignment = Alignment.Center
							) {
								AsyncImage(
									model = imagePath?.absolutePath(),
									contentDescription = null,
									modifier = Modifier
										.size(128.dp)
										.clip(RoundedCornerShape(8.dp)),
									contentScale = ContentScale.Crop
								)
								FilledTonalIconButton(
									modifier = Modifier.align(Alignment.TopEnd),
									onClick = { imagePath = null }
								) {
									Icon(
										Icons.Default.Delete,
										Res.string.encyclopedia_create_entry_remove_image_button.get()
									)
								}
							}
						} else {
							Column(
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.Center
							) {
								Icon(
									Icons.Default.AddPhotoAlternate,
									contentDescription = null,
									modifier = Modifier.size(32.dp),
									tint = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Spacer(modifier = Modifier.height(Ui.Padding.M))
								FilledTonalButton(onClick = { filePickerLauncher.launch() }) {
									Text(Res.string.encyclopedia_create_entry_select_image_button.get())
								}
							}
						}
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)
				) {
					OutlinedButton(
						modifier = Modifier.weight(1f),
						onClick = { component.confirmClose() }
					) {
						Text(Res.string.encyclopedia_create_entry_cancel_button.get())
					}

					Button(
						modifier = Modifier.weight(1f),
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