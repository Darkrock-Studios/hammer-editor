package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
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

	SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
		Box(modifier = Modifier.fillMaxSize()) {
			ChildStack(
				stack = state,
				modifier = Modifier,
				animation = stackAnimation(
					animator = fade(),
					predictiveBackParams = {
						PredictiveBackParams(
							backHandler = component.backHandler,
							onBack = component::onBack,
						)
					},
				),
			) { child ->
				when (val destination = child.instance) {
					is Encyclopedia.Destination.BrowseEntriesDestination -> {
						BrowseEntriesUi(
							component = destination.component,
							scope = scope,
							viewEntry = component::showViewEntry,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack
						)
					}

					is Encyclopedia.Destination.ViewEntryDestination -> {
						ViewEntryUi(
							modifier = Modifier.align(Alignment.TopCenter),
							component = destination.component,
							scope = scope,
							rootSnackbar = rootSnackbar,
							closeEntry = component::showBrowse,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack
						)
					}

					is Encyclopedia.Destination.CreateEntryDestination -> {
						CreateEntryUi(
							component = destination.component,
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
}