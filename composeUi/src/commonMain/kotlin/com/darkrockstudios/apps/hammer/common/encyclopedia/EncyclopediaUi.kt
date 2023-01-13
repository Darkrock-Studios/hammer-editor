package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

	var showCreate by remember { mutableStateOf(false) }

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.PADDING)) {
		if (showCreate) {
			CreateEntry(
				component = component,
				scope = scope,
				modifier = Modifier.align(Alignment.Center),
				snackbarHostState = snackbarHostState
			) {
				showCreate = false
			}
		} else {
			Column {
				Text("Encyclopedia")

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
			}
			FloatingActionButton(
				onClick = { showCreate = true },
				modifier = Modifier.align(Alignment.BottomEnd)
			) {
				Icon(
					Icons.Rounded.Add,
					"Create Entry"
				)
			}
		}
	}

	SnackbarHost(snackbarHostState, modifier = Modifier)
}

@Composable
fun CreateEntry(
	component: Encyclopedia,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState,
	modifier: Modifier,
	close: () -> Unit
) {
	var newEntryNameText by remember { mutableStateOf("") }
	var newEntryContentText by remember { mutableStateOf("") }
	var newTagsText by remember { mutableStateOf("") }
	var newTypeText by remember { mutableStateOf("") }

	Column(modifier = modifier.padding(Ui.PADDING)) {
		Text("Create New Entry")

		TextField(
			value = newEntryNameText,
			onValueChange = { newEntryNameText = it },
			placeholder = { Text("Name") }
		)

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
					close()
					scope.launch { snackbarHostState.showSnackbar("Entry Created") }
				}
			}
		}) {
			Text("Create")
		}
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