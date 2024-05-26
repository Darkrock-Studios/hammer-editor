package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState

@Composable
fun EncyclopediaUi(
	component: Encyclopedia,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.stack.subscribeAsState()

	BoxWithConstraints(modifier = modifier.fillMaxSize()) {
		Children(
			stack = state,
			modifier = Modifier,
			animation = stackAnimation { _ -> fade() },
		) {
			when (val child = it.instance) {
				is Encyclopedia.Destination.BrowseEntriesDestination -> {
					BrowseEntriesUi(
						component = child.component,
						scope = scope,
						viewEntry = component::showViewEntry
					)
				}

				is Encyclopedia.Destination.ViewEntryDestination -> {
					ViewEntryUi(
						modifier = Modifier.align(Alignment.TopCenter),
						component = child.component,
						scope = scope,
						rootSnackbar = rootSnackbar,
						closeEntry = component::showBrowse
					)
				}

				is Encyclopedia.Destination.CreateEntryDestination -> {
					CreateEntryUi(
						component = child.component,
						scope = scope,
						modifier = Modifier.align(Alignment.TopCenter),
						rootSnackbar = rootSnackbar,
						close = component::showBrowse
					)
				}
			}
		}
	}
}