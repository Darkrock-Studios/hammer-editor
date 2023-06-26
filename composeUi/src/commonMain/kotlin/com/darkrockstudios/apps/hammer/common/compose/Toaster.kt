package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.icerock.moko.resources.StringResource

@Composable
fun Toaster(message: StringResource?, snackbarHostState: SnackbarHostState) {
	val strRes = rememberStrRes()
	LaunchedEffect(message) {
		message?.let { message ->
			snackbarHostState.showSnackbar(strRes.get(message))
		}
	}
}