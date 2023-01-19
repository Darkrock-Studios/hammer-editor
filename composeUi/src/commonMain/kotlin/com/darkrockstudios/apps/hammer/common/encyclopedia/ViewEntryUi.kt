package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ViewEntryUi(
	component: ViewEntry,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
	snackbarHostState: SnackbarHostState,
	closeEntry: () -> Unit
) {
	val state by component.state.subscribeAsState()
	var showFilePicker by remember { mutableStateOf(false) }

	var editName by remember { mutableStateOf(false) }
	var entryNameText by remember { mutableStateOf(state.content?.name ?: "") }

	var editText by remember { mutableStateOf(false) }
	var entryText by remember { mutableStateOf(state.content?.text ?: "") }

	LaunchedEffect(state.content) {
		state.content?.let {
			entryNameText = it.name
			entryText = it.text
		}
	}

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val content = state.content
		if (content != null) {
			Column(modifier = modifier.fillMaxHeight().widthIn(128.dp, 420.dp).align(Alignment.TopStart)) {
				Row(horizontalArrangement = Arrangement.End) {
					Button(
						onClick = {
							scope.launch(defaultDispatcher) {
								if (component.deleteEntry(state.entryDef)) {
									withContext(mainDispatcher) {
										closeEntry()
									}
									snackbarHostState.showSnackbar("Entry Deleted")
								}
							}
						}
					) {
						Text("Delete Entry")
					}

					Button(
						onClick = { closeEntry() }
					) {
						Text("X")
					}
				}

				if (state.entryImagePath != null) {
					BoxWithConstraints {
						ImageItem(
							path = state.entryImagePath,
							modifier = Modifier.size(256.dp)
						)
						Button(onClick = {
							scope.launch { component.removeEntryImage() }
						}, modifier.align(Alignment.TopEnd)) {
							Icon(
								Icons.Rounded.Remove,
								"Remove Image"
							)
						}
					}
				} else {
					Button(onClick = { showFilePicker = true }) {
						Text("Select Image")
					}
				}

				Row {
					content.tags.forEach { tag ->
						Text(tag)
					}
				}

				if (editName) {
					TextField(
						modifier = Modifier.fillMaxWidth()
							.padding(PaddingValues(top = Ui.PADDING, bottom = Ui.PADDING)),
						value = entryNameText,
						onValueChange = { entryNameText = it },
						placeholder = { Text("Name") }
					)
				} else {
					Text(
						entryNameText,
						modifier = Modifier.clickable { editName = true }
					)
				}

				if (editText) {
					OutlinedTextField(
						value = entryText,
						onValueChange = { entryText = it },
						modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.PADDING)),
						placeholder = { Text(text = "Describe your entry") },
						maxLines = 10,
					)
				} else {
					Text(
						entryText,
						modifier = Modifier.clickable { editText = true }
					)
				}

				if (editName || editText) {
					Button(onClick = {
						val tags = state.content?.tags ?: return@Button

						scope.launch {
							component.updateEntry(
								name = entryNameText,
								text = entryText,
								tags = tags
							)

							withContext(mainDispatcher) {
								editName = false
								editText = false
							}
						}
					}) {
						Text("Save")
					}

					Button(onClick = {
						scope.launch {
							entryNameText = content.name
							entryText = content.text

							withContext(mainDispatcher) {
								editName = false
								editText = false
							}
						}
					}) {
						Text("Cancel")
					}
				}
			}
		} else {
			CircularProgressIndicator()
		}
	}

	FilePicker(show = showFilePicker, fileExtension = "jpg") { path ->
		if (path != null) {
			scope.launch { component.setImage(path) }
		}
		showFilePicker = false
	}
}