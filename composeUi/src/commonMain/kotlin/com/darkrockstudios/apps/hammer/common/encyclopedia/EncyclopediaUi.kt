package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
fun EncyclopediaUi(component: Encyclopedia) {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	val state by component.state.subscribeAsState()

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.PADDING)) {
		Children(
			stack = state,
			modifier = Modifier,
			animation = stackAnimation { _, _, _ -> fade() },
		) {
			when (val child = it.instance) {
				is Encyclopedia.Destination.BrowseEntriesDestination -> {
					BrowseEntriesUi(
						component = child.component,
						scope = scope,
						showCreate = component::showCreateEntry,
						viewEntry = component::showViewEntry
					)
				}

				is Encyclopedia.Destination.ViewEntryDestination -> {
					ViewEntryUi(
						modifier = Modifier.align(Alignment.TopCenter),
						component = child.component,
						scope = scope,
						closeEntry = component::showBrowse
					)
				}

				is Encyclopedia.Destination.CreateEntryDestination -> {
					CreateEntryUi(
						component = child.component,
						scope = scope,
						modifier = Modifier.align(Alignment.TopCenter),
						snackbarHostState = snackbarHostState,
						close = component::showBrowse
					)
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomEnd))
	}
}