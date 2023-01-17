package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
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
	closeEntry: () -> Unit
) {
	val state by component.state.subscribeAsState()
	var loadContentJob by remember { mutableStateOf<Job?>(null) }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }
	var entryImagePath by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(state.entryDef) {
		entryImagePath = null
		loadContentJob?.cancel()

		val entryDef = state.entryDef
		if (entryDef != null) {
			loadContentJob = scope.launch {
				entryImagePath = component.getImagePath(entryDef)
				val content = component.loadEntryContent(entryDef)
				withContext(mainDispatcher) {
					entryContent = content
					loadContentJob = null
				}
			}
		}
	}

	val content = entryContent
	if (content != null) {
		Column(modifier = modifier.fillMaxHeight()) {
			Row(horizontalArrangement = Arrangement.End) {
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