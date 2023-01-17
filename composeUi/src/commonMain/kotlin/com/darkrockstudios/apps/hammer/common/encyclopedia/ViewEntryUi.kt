package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
	var loadContentJob by remember { mutableStateOf<Job?>(null) }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }
	var entryImagePath by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(state.entryDef) {
		entryImagePath = null
		loadContentJob?.cancel()

		loadContentJob = scope.launch {
			entryImagePath = component.getImagePath(state.entryDef)
			val content = component.loadEntryContent(state.entryDef)
			withContext(mainDispatcher) {
				entryContent = content
				loadContentJob = null
			}
		}
	}

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val content = entryContent
		if (content != null) {

			Column(modifier = modifier.fillMaxHeight().align(Alignment.Center)) {
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

				if (entryImagePath != null) {
					BoxWithConstraints {
						ImageItem(
							path = entryImagePath,
							modifier = Modifier.size(256.dp)
						)
						Button(onClick = { }, modifier.align(Alignment.TopEnd)) {
							Icon(
								Icons.Rounded.Remove,
								"Remove Image"
							)
						}
					}
				}

				Row {
					content.tags.forEach { tag ->
						Text(tag)
					}
				}

				Text(content.name)
				Text(content.text)
			}
		} else {
			CircularProgressIndicator()
		}
	}
}