package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
fun EncyclopediaUi(component: Encyclopedia) {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.PADDING)) {
		CreateEntry(
			component = component,
			scope = scope,
			modifier = Modifier.align(Alignment.TopCenter),
			snackbarHostState = snackbarHostState
		) {
			component.showCreate(false)
		}

		BrowseEntries(
			component = component,
			scope = scope,
			snackbarHostState = snackbarHostState
		) {
			component.showCreate(true)
		}

		ViewEntry(
			modifier = Modifier.align(Alignment.TopCenter),
			component = component,
			scope = scope,
		)

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomEnd))
	}
}