package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class RootSnackbarHostState(
	val snackbarHostState: SnackbarHostState,
	val scope: CoroutineScope
) {
	fun showSnackbar(
		message: String,
		actionLabel: String? = null,
		withDismissAction: Boolean = false,
		duration: SnackbarDuration =
			if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite
	) {
		scope.launch {
			snackbarHostState.showSnackbar(
				message, actionLabel, withDismissAction, duration
			)
		}
	}
}

@Composable
fun rememberRootSnackbarHostState(): RootSnackbarHostState {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }
	return remember { RootSnackbarHostState(snackbarHostState, scope) }
}