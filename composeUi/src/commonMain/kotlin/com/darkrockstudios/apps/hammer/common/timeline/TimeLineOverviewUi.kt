package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import kotlinx.coroutines.CoroutineScope

@Composable
fun TimeLineOverviewUi(
	component: TimeLineOverview,
	scope: CoroutineScope,
	showCreate: () -> Unit,
	viewEvent: (eventId: Int) -> Unit
) {
	val state by component.state.subscribeAsState()

	Box {
		Column {
			Text(
				"Time Line",
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground
			)

			val events = state.timeLine?.events ?: emptyList()
			if (events.isEmpty()) {
				Text(
					"No Events",
					color = MaterialTheme.colorScheme.onBackground,
					style = MaterialTheme.typography.headlineLarge
				)
			}

			DragDropList(
				state.timeLine?.events ?: emptyList(),
				key = { _, item -> item.id },
				onMove = { from, to ->
					state.timeLine?.events?.getOrNull(from)?.let { event ->
						component.moveEvent(event, to, from < to)
					}
				},
				modifier = Modifier.fillMaxSize()
			) { event, isDragging ->
				Card(
					modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth().clickable { viewEvent(event.id) },
					elevation = CardDefaults.elevatedCardElevation(),
					border = if (isDragging) {
						BorderStroke(2.dp, MaterialTheme.colorScheme.tertiaryContainer)
					} else {
						null
					}
				) {
					Column(modifier = Modifier.padding(Ui.Padding.L)) {
						event.date?.let { date ->
							Text(date)
						}
						val content by remember {
							derivedStateOf {
								val maxLength = 256
								if (event.content.length > maxLength) {
									event.content.substring(0, maxLength) + "…"
								} else {
									event.content
								}
							}
						}
						Text(content)
					}
				}
			}
		}

		FloatingActionButton(
			onClick = showCreate,
			modifier = Modifier.align(Alignment.BottomEnd).padding(Ui.Padding.XL)
		) {
			Icon(Icons.Default.Create, "Create Event")
		}
	}
}