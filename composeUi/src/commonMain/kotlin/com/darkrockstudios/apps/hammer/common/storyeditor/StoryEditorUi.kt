package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditor
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.rightBorder
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftCompareUi
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftsListUi
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi

private val LIST_PANE_WIDTH = 300.dp

@Composable
fun StoryEditorUi(
	component: StoryEditor,
	snackbarHostState: RootSnackbarHostState,
	navWidth: Dp = Dp.Unspecified,
	modifier: Modifier = Modifier,
) {
	BoxWithConstraints(modifier = modifier) {
		val state by component.state.subscribeAsState()
		val detailsState by component.detailsRouterState.subscribeAsState()
		val isMultiPane = state.isMultiPane

		val editorDivider = rememberEditorDivider()
		val dividerX =
			if (editorDivider.x != Dp.Unspecified && navWidth != Dp.Unspecified) {
				editorDivider.x - navWidth
			} else {
				LIST_PANE_WIDTH
			}

		val listModifier = if (isMultiPane) {
			Modifier.requiredWidthIn(0.dp, dividerX).fillMaxHeight()
				.rightBorder(1.dp, MaterialTheme.colorScheme.onBackground)
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
			Modifier.padding(start = dividerX)
				.requiredWidthIn(0.dp, maxWidth - dividerX)
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
private fun SetMultiPane(component: StoryEditor) {
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
	routerState: Value<ChildStack<*, StoryEditor.ChildDestination.List>>,
	snackbarHostState: RootSnackbarHostState,
	modifier: Modifier
) {
	val state by routerState.subscribeAsState()

	Children(
		stack = state,
		modifier = modifier,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is StoryEditor.ChildDestination.List.Scenes ->
				SceneListUi(
					component = child.component,
					snackbarHostState = snackbarHostState,
					modifier = Modifier.fillMaxSize(),
				)

			is StoryEditor.ChildDestination.List.None -> Box {}
		}
	}
}

@Composable
private fun DetailsPane(
	state: ChildStack<*, StoryEditor.ChildDestination.Detail>,
	snackbarHostState: RootSnackbarHostState,
	modifier: Modifier,
) {
	Children(
		stack = state,
		modifier = modifier,
		animation = stackAnimation { _ -> fade() },
	) {
		when (val child = it.instance) {
			is StoryEditor.ChildDestination.Detail.None -> Box {}
			is StoryEditor.ChildDestination.Detail.EditorDestination -> {
				SceneEditorUi(
					component = child.component,
					rootSnackbar = snackbarHostState,
					modifier = Modifier.fillMaxSize(),
				)
			}

			is StoryEditor.ChildDestination.Detail.DraftsDestination -> {
				DraftsListUi(component = child.component)
			}

			is StoryEditor.ChildDestination.Detail.DraftCompareDestination -> {
				DraftCompareUi(component = child.component)
			}
		}
	}
}