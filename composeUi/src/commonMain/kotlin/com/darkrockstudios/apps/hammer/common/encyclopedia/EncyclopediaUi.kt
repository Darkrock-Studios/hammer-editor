package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.darkrockstudios.apps.hammer.common.compose.focus.FocusFirstPaneOnArrival
import com.darkrockstudios.apps.hammer.common.compose.focus.PaneId
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeTwiceToLeave
import com.darkrockstudios.apps.hammer.common.compose.focus.escapeToContainer
import com.darkrockstudios.apps.hammer.common.compose.focus.focusPane
import com.darkrockstudios.apps.hammer.common.compose.focus.rememberListCursorState

@Composable
fun EncyclopediaUi(
	component: Encyclopedia,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val state by component.stack.subscribeAsState()
	val destination = state.active.configuration
	val browseCursor = rememberListCursorState()
	LaunchedEffect(destination) {
		if (destination is Encyclopedia.Config.ViewEntryConfig) browseCursor.placeOn(destination.entryDef.id)
	}
	FocusFirstPaneOnArrival(destination, enabled = destination is Encyclopedia.Config.BrowseEntriesConfig)

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
					is Encyclopedia.Destination.BrowseEntriesDestination -> Box(
						modifier = Modifier.fillMaxSize().focusPane(PaneId.List),
					) {
						BrowseEntriesUi(
							component = destination.component,
							scope = scope,
							viewEntry = component::showViewEntry,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@ChildStack,
							cursor = browseCursor,
						)
					}

					is Encyclopedia.Destination.ViewEntryDestination -> {
						ViewEntryUi(
							modifier = Modifier.align(Alignment.TopCenter)
								.escapeToContainer(PaneId.Detail)
								.focusPane(PaneId.Detail),
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
							modifier = Modifier.align(Alignment.TopCenter)
								.escapeTwiceToLeave()
								.focusPane(PaneId.Detail),
							rootSnackbar = rootSnackbar,
							close = component::showBrowse
						)
					}
				}
			}
		}
	}
}