package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTimeLineEventUi(
	component: CreateTimeLineEvent,
	scope: CoroutineScope,
	modifier: Modifier,
	snackbarHostState: SnackbarHostState,
	close: () -> Unit
) {
	val strRes = rememberStrRes()

	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }

	val screen = LocalScreenCharacteristic.current
	val needsExplicitClose = remember { screen.needsExplicitClose }

	Box(modifier = modifier.fillMaxSize()) {
		Card(modifier = Modifier.padding(Ui.Padding.XL).widthIn(max = 512.dp).align(Center)) {
			Column(modifier = Modifier.padding(Ui.Padding.L)) {
				if (needsExplicitClose) {
					IconButton(
						onClick = close,
						modifier = Modifier.align(End).padding(Ui.Padding.XL),
					) {
						Icon(
							Icons.Default.Close,
							MR.strings.timeline_create_close_button.get(),
							tint = MaterialTheme.colorScheme.onBackground
						)
					}
				}
				Text(
					MR.strings.timeline_create_title.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground
				)
				TextField(
					value = dateText,
					onValueChange = { dateText = it },
					label = { Text(MR.strings.timeline_create_date_label.get()) },
					singleLine = true
				)
				OutlinedTextField(
					modifier = Modifier.fillMaxWidth().height(128.dp),
					value = contentText,
					onValueChange = { contentText = it },
					label = { Text(MR.strings.timeline_create_content_label.get()) },
				)

				Button(onClick = {
					scope.launch {
						if (component.createEvent(dateText, contentText)) {
							launch { snackbarHostState.showSnackbar(strRes.get(MR.strings.timeline_create_toast_success)) }
							close()
						} else {
							launch { snackbarHostState.showSnackbar(strRes.get(MR.strings.timeline_create_toast_failure)) }
						}
					}
				}) {
					Text(MR.strings.timeline_create_create_event_button.get())
				}
			}
		}
	}
}