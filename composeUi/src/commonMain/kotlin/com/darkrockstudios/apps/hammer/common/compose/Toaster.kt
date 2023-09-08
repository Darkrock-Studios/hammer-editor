package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster

@Composable
fun Toaster(component: ComponentToaster, snackbarHostState: SnackbarHostState) {
	val strRes = rememberStrRes()
	LaunchedEffect(Unit) {
		component.toast.collect { message ->
			val msg = strRes.get(message.stringResource, *message.params)
			snackbarHostState.showSnackbar(msg)
		}
	}
}