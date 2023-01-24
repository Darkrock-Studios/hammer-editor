package com.darkrockstudios.apps.hammer.common.projecteditor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.*
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsListUi
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListUi

private val LIST_PANE_WIDTH = 300.dp

@Composable
fun ProjectEditorUi(
	component: ProjectEditor,
	modifier: Modifier = Modifier,
	isWide: Boolean,
	drawableKlass: Any? = null
) {
	BoxWithConstraints(modifier = modifier) {
		val state by component.state.subscribeAsState()
		val detailsState by component.detailsRouterState.subscribeAsState()
		val isMultiPane = state.isMultiPane

		Row {
			val listModifier = if (isMultiPane) {
				Modifier.requiredWidthIn(0.dp, LIST_PANE_WIDTH)
			} else {
				Modifier.fillMaxSize()
			}

			// List
			if ((!isMultiPane && !component.isDetailShown()) || isMultiPane) {
				ListPane(
					routerState = component.listRouterState,
					modifier = listModifier,
				)
			}

			// Detail
			DetailsPane(
				state = detailsState,
				modifier = Modifier.fillMaxWidth(),
				drawableKlass = drawableKlass
			)
		}

		LaunchedEffect(isWide) {
			component.setMultiPane(isWide)
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
	state: ChildStack<*, ProjectEditor.ChildDestination.Detail>,
	modifier: Modifier,
	drawableKlass: Any? = null
) {
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