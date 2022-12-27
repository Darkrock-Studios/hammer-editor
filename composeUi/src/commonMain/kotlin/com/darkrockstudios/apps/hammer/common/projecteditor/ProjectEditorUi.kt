package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.*
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsListUi
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListUi

private val MULTI_PANE_WIDTH_THRESHOLD = 800.dp
private const val DETAILS_PANE_WEIGHT = 2F
private val LIST_PANE_WIDTH = 512.dp

@Composable
fun ProjectEditorUi(
	component: ProjectEditor,
	modifier: Modifier = Modifier,
	drawableKlass: Any? = null
) {
	BoxWithConstraints(modifier = modifier) {
		val state by component.state.subscribeAsState()

		val isMultiPane = state.isMultiPane

		Row {
			val listModifier = if (isMultiPane) {
				Modifier.requiredWidthIn(0.dp, LIST_PANE_WIDTH)
			} else {
				Modifier.weight(1f)
			}

			// List
			Column(modifier = listModifier) {
				ListPane(
					routerState = component.listRouterState,
					modifier = Modifier.fillMaxSize(),
				)
			}

			val detailsModifier = if (isMultiPane) {
				Modifier.weight(DETAILS_PANE_WEIGHT)
			} else {
				Modifier.fillMaxSize()
			}

			// Detail
			Column(modifier = detailsModifier) {
				DetailsPane(
					routerState = component.detailsRouterState,
					modifier = Modifier.fillMaxWidth(),
					drawableKlass = drawableKlass
				)
			}
		}

		val isMultiPaneRequired = this@BoxWithConstraints.maxWidth >= MULTI_PANE_WIDTH_THRESHOLD
		LaunchedEffect(isMultiPaneRequired) {
			component.setMultiPane(isMultiPaneRequired)
		}
	}
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun ListPane(
	routerState: Value<ChildStack<*, ProjectEditor.ChildDestination.List>>,
	modifier: Modifier
) {
	val state by routerState.subscribeAsState()

	Children(
			stack = state,
			modifier = modifier,
			animation = stackAnimation { _, _, _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectEditor.ChildDestination.List.Scenes ->
				SceneListUi(
					component = child.component,
					modifier = Modifier.fillMaxSize()
				)

			is ProjectEditor.ChildDestination.List.None -> Box {}
		}
	}
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun DetailsPane(
	routerState: Value<ChildStack<*, ProjectEditor.ChildDestination.Detail>>,
	modifier: Modifier,
	drawableKlass: Any? = null
) {
	val state by routerState.subscribeAsState()

	Children(
			stack = state,
			modifier = modifier,
			animation = stackAnimation { _, _, _ -> fade() },
	) {
		when (val child = it.instance) {
			is ProjectEditor.ChildDestination.Detail.None -> Box {}
			is ProjectEditor.ChildDestination.Detail.EditorDestination -> {
				SceneEditorUi(
					component = child.component,
					modifier = Modifier.fillMaxSize(),
					drawableKlass = drawableKlass
				)
			}
			is ProjectEditor.ChildDestination.Detail.DraftsDestination -> {
				DraftsListUi(
					component = child.component,
					modifier = Modifier.fillMaxSize()
				)
			}
		}
	}
}