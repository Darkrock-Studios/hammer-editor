package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KFunction0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewTimeLineEventUi(
    component: ViewTimeLineEvent,
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    closeEvent: KFunction0<Unit>
) {
	val dispatcherMain = rememberMainDispatcher()
	val dispatcherDefault = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()

	var editDate by remember { mutableStateOf(false) }
	var eventDateText by remember(state.event) { mutableStateOf(state.event?.date ?: "") }

	var editContent by remember { mutableStateOf(false) }
	var eventText by remember(state.event) { mutableStateOf(state.event?.content ?: "") }

	val screen = LocalScreenCharacteristic.current
	val event = state.event

	Box(
		modifier = modifier.fillMaxSize().padding(Ui.Padding.XL),
		contentAlignment = Alignment.TopCenter
	) {
		Column(modifier = Modifier.widthIn(128.dp, 700.dp).wrapContentHeight()) {
			Row(
				modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				if (event != null && (editDate || editContent)) {
					IconButton(onClick = {
						scope.launch(dispatcherDefault) {
							component.updateEvent(
								event.copy(
									date = eventDateText,
									content = eventText
								)
							)

							withContext(dispatcherMain) {
								editDate = false
								editContent = false
							}

							scope.launch {
								snackbarHostState.showSnackbar("Entry Saved")
							}
						}
					}) {
						Icon(
							Icons.Filled.Check,
							"Save",
							tint = MaterialTheme.colorScheme.onSurface
						)
					}

					IconButton(onClick = {
						scope.launch {
							eventDateText = event.date ?: ""
							eventText = event.content

							withContext(dispatcherMain) {
								editDate = false
								editContent = false
							}
						}
					}) {
						Icon(
							Icons.Filled.Cancel,
							"Cancel",
							tint = MaterialTheme.colorScheme.error
						)
					}

					if (screen.needsExplicitClose) {
						Spacer(modifier = Modifier.size(Ui.Padding.XL))

						Divider(
							color = MaterialTheme.colorScheme.outline,
							modifier = Modifier.fillMaxHeight().width(1.dp)
								.padding(top = Ui.Padding.M, bottom = Ui.Padding.M)
						)

						Spacer(modifier = Modifier.size(Ui.Padding.XL))
					}
				}

				if (screen.needsExplicitClose) {
					IconButton(
						onClick = closeEvent,
					) {
						Icon(
							Icons.Filled.Close,
							contentDescription = "Close Entry",
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			if (event != null) {
				event.date?.let { date ->
					if (editDate) {
						TextField(
							modifier = Modifier.wrapContentHeight().fillMaxWidth(),
							value = eventDateText,
							onValueChange = { eventDateText = it },
							placeholder = { Text("Date") }
						)
					} else {
						Text(
							date,
							style = MaterialTheme.typography.displayMedium,
							color = MaterialTheme.colorScheme.onBackground,
							modifier = Modifier.wrapContentHeight().fillMaxWidth().clickable { editDate = true }
						)
					}
				}

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				if (editContent) {
					OutlinedTextField(
						value = eventText,
						onValueChange = { eventText = it },
						modifier = Modifier.fillMaxWidth().padding(PaddingValues(bottom = Ui.Padding.XL)),
						placeholder = { Text(text = "Describe your event") },
						maxLines = 10,
					)
				} else {
					Text(
						event.content,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onBackground,
						modifier = Modifier.wrapContentHeight().fillMaxWidth().clickable { editContent = true }
					)
				}
			}
		}
	}
}