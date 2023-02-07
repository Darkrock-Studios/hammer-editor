package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLineUi(component: TimeLine) {
	val state by component.state.subscribeAsState()

	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }

	Column {
		Text(
			"Time Line",
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onBackground
		)
		TextField(
			value = dateText,
			onValueChange = { dateText = it },
			label = { Text("Date (optional)") },
			singleLine = true
		)
		OutlinedTextField(
			modifier = Modifier.fillMaxWidth().height(128.dp),
			value = contentText,
			onValueChange = { contentText = it },
			label = { Text("Content") },
		)

		Button(onClick = {
			component.createEvent(dateText, contentText)
		}) {
			Text("Create")
		}

		val events = state.timeLine?.events ?: emptyList()
		if (events.isEmpty()) {
			Text("No Events")
		}
		DragDropList(
			state.timeLine?.events ?: emptyList(),
			key = { _, item -> item.id },
			onMove = { from, to ->
				state.timeLine?.events?.getOrNull(from)?.let { event ->
					component.moveEvent(event, to)
				}
			},
			modifier = Modifier.fillMaxSize()
		) { event ->
			Card(
				modifier = Modifier.padding(Ui.Padding.L),
				elevation = CardDefaults.elevatedCardElevation()
			) {
				Column(modifier = Modifier.padding(Ui.Padding.L)) {
					event.date?.let { date ->
						Text(date)
					}
					Text(event.content)
				}
			}
		}
	}
}
