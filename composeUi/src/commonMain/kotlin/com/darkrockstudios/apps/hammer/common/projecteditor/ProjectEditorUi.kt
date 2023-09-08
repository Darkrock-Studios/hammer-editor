package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.ProjectEditor
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftCompareUi
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsListUi
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListUi

private val LIST_PANE_WIDTH = 300.dp

@Composable
fun ProjectEditorUi(
	component: ProjectEditor,
	snackbarHostState: SnackbarHostState,
	modifier: Modifier = Modifier,
) {
	BoxWithConstraints(modifier = modifier) {
		val state by component.state.subscribeAsState()
		val detailsState by component.detailsRouterState.subscribeAsState()
		val isMultiPane = state.isMultiPane

		val listModifier = if (isMultiPane) {
			Modifier.requiredWidthIn(0.dp, LIST_PANE_WIDTH).fillMaxHeight()
		} else {
			Modifier.fillMaxSize()
		}

		//val shouldShowList = (!isMultiPane && !component.isDetailShown()) || isMultiPane
		ListPane(
			routerState = component.listRouterState,
			snackbarHostState = snackbarHostState,
			modifier = listModifier,
		)

		val detailsModifier = if (isMultiPane) {
			Modifier.padding(start = LIST_PANE_WIDTH)
				.requiredWidthIn(0.dp, maxWidth - LIST_PANE_WIDTH)
				.fillMaxHeight()
		} else {
			Modifier.fillMaxSize()
		}
		// Detail
		DetailsPane(
			state = detailsState,
			snackbarHostState = snackbarHostState,
			modifier = detailsModifier,
		)
	}

	SetMultiPane(component)
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun SetMultiPane(component: ProjectEditor) {
	/*
	// On first try, I didn't like how this worked out, may try again later
	val windowSizeClass = calculateWindowSizeClass()
	val isWide = when (windowSizeClass.widthSizeClass) {
		WindowWidthSizeClass.Compact, WindowWidthSizeClass.Medium -> false
		WindowWidthSizeClass.Expanded -> true
		else -> error("Unhandled Window class")
	}
	*/
	val isWide = LocalScreenCharacteristic.current.isWide
	LaunchedEffect(isWide) {
		component.setMultiPane(isWide)
	}
}

@Composable
private fun ListPane(
	routerState: Value<ChildStack<*, ProjectEditor.ChildDestination.List>>,
	snackbarHostState: SnackbarHostState,
	modifier: Modifier
) {
	val state by routerState.subscribeAsState()

	Children(
		stack = state,
		modifier = modifier,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectEditor.ChildDestination.List.Scenes ->
				SceneListUi(
					component = child.component,
					snackbarHostState = snackbarHostState,
					modifier = Modifier.fillMaxSize(),
				)

			is ProjectEditor.ChildDestination.List.None -> Box {}
		}
	}
}

@Composable
private fun DetailsPane(
	state: ChildStack<*, ProjectEditor.ChildDestination.Detail>,
	snackbarHostState: SnackbarHostState,
	modifier: Modifier,
) {
	Children(
		stack = state,
		modifier = modifier,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectEditor.ChildDestination.Detail.None -> Box {}
			is ProjectEditor.ChildDestination.Detail.EditorDestination -> {
				SceneEditorUi(
					component = child.component,
					snackbarHostState = snackbarHostState,
					modifier = Modifier.fillMaxSize(),
				)
			}

			is ProjectEditor.ChildDestination.Detail.DraftsDestination -> {
				DraftsListUi(component = child.component)
			}

			is ProjectEditor.ChildDestination.Detail.DraftCompareDestination -> {
				DraftCompareUi(component = child.component)
			}
		}
	}
}