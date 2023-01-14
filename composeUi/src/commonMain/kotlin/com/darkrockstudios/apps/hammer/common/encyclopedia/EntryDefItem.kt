package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EntryDefItem(
	entryDef: EntryDef,
	component: Encyclopedia,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	var loadContentJob = remember<Job?> { null }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }

	LaunchedEffect(entryDef) {
		loadContentJob?.cancel()
		loadContentJob = scope.launch {
			val content = component.loadEntryContent(entryDef)
			withContext(mainDispatcher) {
				entryContent = content
				loadContentJob = null
			}
		}
	}

	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(Ui.PADDING),
		elevation = Ui.ELEVATION
	) {
		Column {
			Text(entryDef.id.toString())
			Text(entryDef.type.text)
			Text(entryDef.name)

			if (loadContentJob != null) {
				CircularProgressIndicator()
			} else {
				val content = entryContent
				if (content != null) {
					Text("Content: " + content.text)

					Row {
						content.tags.forEach { tag ->
							Text(tag)
						}
					}
				} else {
					Text("Error: Failed to load entry!")
				}
			}
		}
	}
}