package com.darkrockstudios.apps.hammer.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.wear.data.ACCESS_LOCAL_NETWORK

/**
 * Shows the system local network prompt. Whether it is needed at all is the component's call, made
 * from the server address; this only asks. [onResult] runs whichever way the user answers.
 */
@Composable
fun rememberLocalNetworkPermissionRequest(onResult: () -> Unit): () -> Unit {
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
		onResult()
	}
	return { launcher.launch(ACCESS_LOCAL_NETWORK) }
}

/**
 * Asks once, the first time [needed] turns true, rather than on every recomposition or rotation.
 * Asking again is left to an explicit tap.
 */
@Composable
fun AskForLocalNetworkOnce(needed: Boolean, request: () -> Unit) {
	var asked by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(needed) {
		if (needed && !asked) {
			asked = true
			request()
		}
	}
}
