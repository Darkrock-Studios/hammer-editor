package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@Composable
fun Modifier.moveFocusOnTab(
	focusManager: FocusManager = LocalFocusManager.current
) = onPreviewKeyEvent {
	if (it.type == KeyEventType.KeyDown && it.key == Key.Tab) {
		focusManager.moveFocus(
			if (it.isShiftPressed) FocusDirection.Previous
			else FocusDirection.Next
		)
		return@onPreviewKeyEvent true
	}
	false
}

fun <T> serializableSaver(serializer: KSerializer<T>): Saver<T, String> {
	return Saver(
		save = { data ->
			Json.encodeToString(serializer, data)
		},
		restore = { json ->
			Json.decodeFromString(serializer, json)
		}
	)
}

fun <T> serializableStateSaver(serializer: KSerializer<T>): Saver<MutableState<T>, String> {
	return Saver(
		save = { data ->
			Json.encodeToString(serializer, data.value)
		},
		restore = { json ->
			val value = Json.decodeFromString(serializer, json)
			mutableStateOf(value)
		}
	)
}