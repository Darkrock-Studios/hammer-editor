package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLineUi(component: TimeLine) {
	val state by component.state.subscribeAsState()

	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }

	Column {
		Text("Time Line")
		TextField(
			value = dateText,
			onValueChange = { dateText = it },
			label = { Text("Date (optional)") },
			singleLine = true
		)
		OutlinedTextField(
			modifier = Modifier.fillMaxWidth().height(256.dp),
			value = contentText,
			onValueChange = { contentText = it },
			label = { Text("Content") },
		)

		Button(onClick = {
			component.createEvent(dateText, contentText)
		}) {
			Text("Create")
		}

		LazyColumn(
			modifier = Modifier.fillMaxWidth(),
			contentPadding = PaddingValues(Ui.Padding.XL)
		) {
			val events = state.timeLine?.events
			if (events.isNullOrEmpty()) {
				item {
					Text("No Events")
				}
			} else {
				items(events.size) { index ->
					val event = events[index]
					event.date?.let { date ->
						Text(date)
					}
					Text(event.content)
				}
			}
		}
	}
}