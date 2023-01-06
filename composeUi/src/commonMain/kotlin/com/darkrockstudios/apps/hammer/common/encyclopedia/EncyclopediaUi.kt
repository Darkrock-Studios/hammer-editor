package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EncyclopediaUi(component: Encyclopedia) {

	val state by component.state.subscribeAsState()

	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	var newEntryNameText by remember { mutableStateOf("") }
	var newEntryContentText by remember { mutableStateOf("") }
	var newTagsText by remember { mutableStateOf("") }
	var newTypeText by remember { mutableStateOf("") }

	Column {
		Text("Encyclopedia")
		TextField(
			value = newEntryNameText,
			onValueChange = { newEntryNameText = it },
			placeholder = { Text("Name") }
		)
		TextField(
			value = newEntryContentText,
			onValueChange = { newEntryContentText = it },
			placeholder = { Text("Content") }
		)
		TextField(
			value = newTypeText,
			onValueChange = { newTypeText = it },
			placeholder = { Text("Type") }
		)
		TextField(
			value = newTagsText,
			onValueChange = { newTagsText = it },
			placeholder = { Text("Tags (space seperated)") }
		)
		Button(onClick = {
			val result = component.createEntry(
				name = newEntryNameText,
				type = EntryType.fromString(newTypeText),
				text = newEntryContentText,
				tags = newTagsText.splitToSequence(" ").toList()
			)
			when (result.error) {
				EntryError.NAME_TOO_LONG -> scope.launch { snackbarHostState.showSnackbar("Entry Name was too long") }
				EntryError.NONE -> {
					newEntryNameText = ""
					scope.launch { snackbarHostState.showSnackbar("Entry Created") }
				}
			}
		}) {
			Text("Create")
		}
		Spacer(modifier = Modifier)

		Text("Notes")
		LazyVerticalGrid(
			columns = GridCells.Adaptive(512.dp),
			modifier = Modifier.fillMaxWidth(),
			contentPadding = PaddingValues(Ui.PADDING)
		) {
			if (state.entryDefs.isEmpty()) {
				item {
					Text("No Entries Found")
				}
			} else {
				items(state.entryDefs.size) { index ->
					EntryDefItem(
						entry = state.entryDefs[index],
						component = component,
						snackbarHostState = snackbarHostState,
						scope = scope
					)
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier)
	}
}

@Composable
fun EntryDefItem(
	entry: EntryDef,
	component: Encyclopedia,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(Ui.PADDING),
		elevation = Ui.ELEVATION
	) {
		Column {
			Text(entry.id.toString())
			Text(entry.type.text)
			Text(entry.name)
		}
	}
}