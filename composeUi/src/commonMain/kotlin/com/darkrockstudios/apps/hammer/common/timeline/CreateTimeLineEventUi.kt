package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CreateTimeLineEventUi(
	component: CreateTimeLineEvent,
	scope: CoroutineScope,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()

	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }

	val screen = LocalScreenCharacteristic.current
	val needsExplicitClose = remember { screen.needsExplicitClose }

	Box(modifier = modifier.fillMaxSize()) {
		Card(
			modifier = Modifier.padding(Ui.Padding.XL).widthIn(max = 512.dp).align(Center),
			elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
		) {
			Column(
				modifier = Modifier.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
				) {
					Text(
						Res.string.timeline_create_title.get(),
						style = MaterialTheme.typography.headlineLarge
					)
					if (needsExplicitClose) {
						IconButton(onClick = component::closeCreation) {
							Icon(
								Icons.Default.Close,
								Res.string.timeline_create_close_button.get(),
								tint = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}

				OutlinedTextField(
					modifier = Modifier.fillMaxWidth(),
					value = dateText,
					onValueChange = { dateText = it },
					label = { Text(Res.string.timeline_create_date_label.get()) },
					singleLine = true
				)

				OutlinedTextField(
					modifier = Modifier.fillMaxWidth().height(128.dp),
					value = contentText,
					onValueChange = { contentText = it },
					label = { Text(Res.string.timeline_create_content_label.get()) },
				)

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)
				) {
					OutlinedButton(
						modifier = Modifier.weight(1f),
						onClick = component::closeCreation
					) {
						Text(Res.string.timeline_create_close_button.get())
					}

					Button(
						modifier = Modifier.weight(1f),
						onClick = {
							scope.launch {
								if (component.createEvent(dateText, contentText)) {
									launch { rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_create_toast_success)) }
									component.closeCreation()
								} else {
									launch { rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_create_toast_failure)) }
								}
							}
						}
					) {
						Text(Res.string.timeline_create_create_event_button.get())
					}
				}
			}
		}
	}
}