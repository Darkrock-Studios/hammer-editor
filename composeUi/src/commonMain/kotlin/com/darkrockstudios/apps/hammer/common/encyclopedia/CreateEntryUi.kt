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
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.getHomeDirectory
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
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
	var newEntryNameText by rememberSaveable { mutableStateOf("") }
	var newEntryContentText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(TextFieldValue(""))
	}
	var newTagsText by rememberSaveable { mutableStateOf("") }
	var selectedType by rememberSaveable { mutableStateOf(EntryType.PERSON) }
	val types = rememberSaveable { EntryType.values().toList() }

	var showFilePicker by rememberSaveable { mutableStateOf(false) }
	var imagePath by rememberSaveable { mutableStateOf<String?>(null) }

	var discardConfirm by rememberSaveable { mutableStateOf(false) }

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
					"Create New Entry",
					modifier = Modifier.padding(PaddingValues(bottom = Ui.Padding.XL)),
					style = MaterialTheme.typography.displayMedium
				)

				Text(
					"Type:",
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.padding(bottom = Ui.Padding.M)
				)

				ExposedDropDown(
					modifier = Modifier.fillMaxWidth(),
					padding = Ui.Padding.XL,
					items = types,
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
					placeholder = { Text("Name") }
				)

				TextField(
					modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.Padding.L)),
					value = newTagsText,
					onValueChange = { newTagsText = it },
					placeholder = { Text("Tags (space seperated)") }
				)

				OutlinedTextField(
					value = newEntryContentText,
					onValueChange = { newEntryContentText = it },
					modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.Padding.L)),
					placeholder = { Text(text = "Describe your entry") },
					maxLines = 10,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Box(
					modifier = Modifier.fillMaxWidth()
						.border(width = 1.dp, color = MaterialTheme.colorScheme.outline),
					contentAlignment = Alignment.Center
				) {
					if (imagePath != null) {
						Box(modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)) {
							ImageItem(
								modifier = Modifier.size(128.dp).background(Color.LightGray),
								path = imagePath
							)
							Button(
								modifier = Modifier
									.padding(PaddingValues(start = Ui.Padding.XL))
									.align(Alignment.TopEnd),
								onClick = { imagePath = null }
							) {
								Icon(Icons.Default.Delete, "Remove Image")
							}
						}
					} else {
						Button(onClick = { showFilePicker = true }) {
							Text("Select Image")
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
									imagePath = imagePath
								)
								when (result.error) {
									EntryError.NAME_TOO_LONG -> scope.launch { snackbarHostState.showSnackbar("Entry Name was too long. Max ${EncyclopediaRepository.MAX_NAME_SIZE}") }
									EntryError.NAME_INVALID_CHARACTERS -> scope.launch {
										snackbarHostState.showSnackbar(
											"Entry Name must be alpha-numeric"
										)
									}

									EntryError.TAG_TOO_LONG -> scope.launch { snackbarHostState.showSnackbar("Tag is too long. Max ${EncyclopediaRepository.MAX_TAG_SIZE}") }
									EntryError.NONE -> {
										newEntryNameText = ""
										close()
										scope.launch { snackbarHostState.showSnackbar("Entry Created") }
									}
								}
							}
						}
					) {
						Text("Create")
					}

					Button(
						modifier = Modifier.weight(1f).padding(PaddingValues(start = Ui.Padding.XL)),
						onClick = { discardConfirm = true }
					) {
						Text("Cancel")
					}
				}
			}
		}
	}

	FilePicker(
		show = showFilePicker,
		fileExtension = "jpg",
		initialDirectory = getHomeDirectory()
	) { path ->
		imagePath = path
		showFilePicker = false
	}

	if (discardConfirm) {
		SimpleConfirm(
			title = "Discard New Entry?",
			onDismiss = { discardConfirm = false }
		) {
			discardConfirm = false
			close()
		}
	}
}