package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
fun EncyclopediaUi(component: Encyclopedia) {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	var showCreate by remember { mutableStateOf(false) }

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.PADDING)) {
		if (showCreate) {
			CreateEntry(
				component = component,
				scope = scope,
				modifier = Modifier.align(Alignment.TopCenter),
				snackbarHostState = snackbarHostState
			) {
				showCreate = false
			}
		} else {
			BrowseEntries(
				component = component,
				scope = scope,
				snackbarHostState = snackbarHostState
			) {
				showCreate = true
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomEnd))
	}
}