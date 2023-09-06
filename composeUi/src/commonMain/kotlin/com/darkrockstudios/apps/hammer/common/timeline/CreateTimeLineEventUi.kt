package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun CreateTimeLineEventUi(
	component: CreateTimeLineEvent,
	scope: CoroutineScope,
	modifier: Modifier,
	snackbarHostState: SnackbarHostState,
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
						onClick = component::closeCreation,
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
							component.closeCreation()
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