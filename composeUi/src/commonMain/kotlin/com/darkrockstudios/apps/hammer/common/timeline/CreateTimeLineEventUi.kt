package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
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
	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }

	val screen = LocalScreenCharacteristic.current
	val needsExplicitClose = remember { screen.needsExplicitClose }

	Column(modifier = modifier) {
		if (needsExplicitClose) {
			IconButton(
				onClick = close,
				modifier = Modifier.align(End).padding(Ui.Padding.XL),
			) {
				Icon(
					Icons.Default.Close,
					"Close",
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}
		Text(
			"Time Line Event",
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
			scope.launch {
				if (component.createEvent(dateText, contentText)) {
					launch { snackbarHostState.showSnackbar("Event Created") }
					close()
				} else {
					launch { snackbarHostState.showSnackbar("Failed to create event") }
				}
			}
		}) {
			Text("Create")
		}
	}
}