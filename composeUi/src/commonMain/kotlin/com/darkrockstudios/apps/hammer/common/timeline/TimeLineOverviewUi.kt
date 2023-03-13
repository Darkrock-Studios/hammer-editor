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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import kotlinx.coroutines.CoroutineScope

const val TIME_LINE_CREATE_TAG = "Timeline Overview Create"
const val TIME_LINE_LIST_TAG = "Timeline Overview List"

@Composable
fun TimeLineOverviewUi(
    component: TimeLineOverview,
    scope: CoroutineScope,
    showCreate: () -> Unit,
    viewEvent: (eventId: Int) -> Unit
) {
	val state by component.state.subscribeAsState()

	Box(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
		Column(modifier = Modifier.widthIn(0.dp, 700.dp).fillMaxWidth()) {
			Text(
				"Time Line",
				style = MaterialTheme.typography.headlineLarge,
				color = MaterialTheme.colorScheme.onBackground
			)

			val events = state.timeLine?.events ?: emptyList()
			if (events.isEmpty()) {
				Text(
					"No Events",
					modifier = Modifier.fillMaxWidth(),
					textAlign = TextAlign.Center,
					color = MaterialTheme.colorScheme.onBackground,
					style = MaterialTheme.typography.headlineSmall
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
				modifier = Modifier.fillMaxSize().testTag(TIME_LINE_LIST_TAG)
			) { event, isDragging ->
				EventCard(event, isDragging, viewEvent)
			}
		}

		FloatingActionButton(
			onClick = showCreate,
			modifier = Modifier.align(Alignment.BottomEnd).testTag(TIME_LINE_CREATE_TAG)
		) {
			Icon(Icons.Default.Create, "Create Event")
		}
	}
}

const val EVENT_CARD_TAG = "Timeline Event Card"
const val EVENT_CARD_DATE_TAG = "Timeline Event Card Date"
const val EVENT_CARD_CONTENT_TAG = "Timeline Event Card Content"
const val EVENT_CARD_MAX_CONTENT_LENGTH = 256

@Composable
fun EventCard(event: TimeLineEvent, isDragging: Boolean, viewEvent: (eventId: Int) -> Unit) {
	val lineColor = MaterialTheme.colorScheme.outline
	Box(
		modifier = Modifier
			.fillMaxSize()
			.drawBehind {
				val pad = Ui.Padding.XL.toPx()

				drawLine(
					color = lineColor,
					start = Offset(pad, 0f),
					end = Offset(pad, size.height),
					strokeWidth = Stroke.DefaultMiter,
					cap = StrokeCap.Round
				)

				drawLine(
					color = lineColor,
					start = Offset(pad, size.height / 2f),
					end = Offset(pad * 3f, size.height / 2f),
					strokeWidth = Stroke.DefaultMiter,
					cap = StrokeCap.Round
				)
			}
	) {
		Column {
			event.date?.let { date ->
				Text(
					modifier = Modifier
						.padding(start = Ui.Padding.XL + Ui.Padding.L)
						.testTag(EVENT_CARD_DATE_TAG),
					text = date,
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onBackground
				)
			}

			Card(
				modifier = Modifier.padding(
					start = Ui.Padding.XL * 3,
					end = Ui.Padding.XL,
					top = Ui.Padding.XL,
					bottom = Ui.Padding.XL
				).fillMaxWidth()
					.clickable { viewEvent(event.id) }
					.testTag(EVENT_CARD_TAG),
				elevation = CardDefaults.elevatedCardElevation(),
				border = if (isDragging) {
					BorderStroke(2.dp, MaterialTheme.colorScheme.tertiaryContainer)
				} else {
					null
				}
			) {
				Column(modifier = Modifier.padding(Ui.Padding.L)) {
					val content by remember {
						derivedStateOf {
							if (event.content.length > EVENT_CARD_MAX_CONTENT_LENGTH) {
								event.content.substring(0, EVENT_CARD_MAX_CONTENT_LENGTH - 1) + "…"
							} else {
								event.content
							}
						}
					}
					Text(
						content,
						modifier = Modifier.testTag(EVENT_CARD_CONTENT_TAG)
					)
				}
			}
		}
	}
}