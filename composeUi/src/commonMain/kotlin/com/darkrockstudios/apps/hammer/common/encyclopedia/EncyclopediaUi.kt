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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.DropDown
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

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomEnd))
	}
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
	var newEntryContentText by remember { mutableStateOf(TextFieldValue("")) }
	var newTagsText by remember { mutableStateOf("") }
	var selectedType by remember { mutableStateOf(EntryType.PERSON) }

	Column(modifier = modifier.padding(Ui.PADDING).widthIn(128.dp, 420.dp)) {

		Text(
			"Create New Entry",
			modifier = Modifier.padding(PaddingValues(bottom = Ui.PADDING)),
			style = MaterialTheme.typography.h4
		)

		Text("Type:", modifier = Modifier.padding(bottom = 2.dp))
		val types = EntryType.values().toList()
		DropDown(
			modifier = Modifier.padding(Ui.PADDING).fillMaxWidth(),
			items = types,
			defaultIndex = types.indexOf(EntryType.PERSON)
		) { item ->
			selectedType = item
		}

		TextField(
			modifier = Modifier.fillMaxWidth().padding(PaddingValues(top = Ui.PADDING, bottom = Ui.PADDING)),
			value = newEntryNameText,
			onValueChange = { newEntryNameText = it },
			placeholder = { Text("Name") }
		)

		TextField(
			modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.PADDING)),
			value = newTagsText,
			onValueChange = { newTagsText = it },
			placeholder = { Text("Tags (space seperated)") }
		)

		OutlinedTextField(
			value = newEntryContentText,
			onValueChange = { newEntryContentText = it },
			modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.PADDING)),
			placeholder = { Text(text = "Describe your entry") },
			maxLines = 10,
		)

		Row(modifier = Modifier.fillMaxWidth()) {
			Button(
				modifier = Modifier.weight(1f).padding(PaddingValues(end = Ui.PADDING)),
				onClick = {
					val result = component.createEntry(
						name = newEntryNameText,
						type = selectedType,
						text = newEntryContentText.text,
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
				}
			) {
				Text("Create")
			}

			Button(
				modifier = Modifier.weight(1f).padding(PaddingValues(start = Ui.PADDING)),
				onClick = { close() }
			) {
				Text("Cancel")
			}
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