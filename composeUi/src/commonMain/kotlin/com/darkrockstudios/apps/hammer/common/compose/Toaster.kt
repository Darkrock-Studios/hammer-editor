package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import dev.icerock.moko.resources.StringResource

@Composable
fun Toaster(message: StringResource?, snackbarHostState: SnackbarHostState) {
	val strRes = rememberStrRes()
	val messageStr = rememberSaveable(message) { message?.run { strRes.get(this) } ?: "" }
	Toaster(messageStr, snackbarHostState)
}

@Composable
fun Toaster(message: String?, snackbarHostState: SnackbarHostState) {
	val messageText = rememberSaveable(message) { message ?: "" }
	LaunchedEffect(messageText) {
		if (messageText.isNotBlank()) snackbarHostState.showSnackbar(messageText)
	}
}