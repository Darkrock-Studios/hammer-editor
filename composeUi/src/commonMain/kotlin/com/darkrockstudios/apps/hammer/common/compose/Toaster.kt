package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster

@Composable
fun Toaster(component: ComponentToaster, rootSnackbar: RootSnackbarHostState) {
	val strRes = rememberStrRes()
	LaunchedEffect(Unit) {
		component.toast.collect { message ->
			val msg = strRes.get(message.stringResource, *message.params)
			rootSnackbar.showSnackbar(msg)
		}
	}
}