package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.essenty.backhandler.BackHandler
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.StoryEditor
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.focus.PaneId
import com.darkrockstudios.apps.hammer.common.compose.focus.focusPane
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.rightBorder
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftCompareUi
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftsListUi
import com.darkrockstudios.apps.hammer.common.storyeditor.readermode.ReaderModeUi
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
import com.darkrockstudios.apps.hammer.scene_editor_no_scene_selected
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPageEmpty

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
				.rightBorder(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant)
		} else {
			Modifier.fillMaxSize()
		}

		//val shouldShowList = (!isMultiPane && !component.isDetailShown()) || isMultiPane
		val listState by component.listRouterState.subscribeAsState()
		ListPane(
			state = listState,
			snackbarHostState = snackbarHostState,
			modifier = listModifier.focusPane(
				PaneId.List,
				enabled = listState.active.instance !is StoryEditor.ChildDestination.List.None,
			),
			isMultiPane = isMultiPane,
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
			isMultiPane = isMultiPane,
			snackbarHostState = snackbarHostState,
			modifier = detailsModifier.focusPane(
				PaneId.Detail,
				enabled = detailsState.active.instance !is StoryEditor.ChildDestination.Detail.None,
			),
			backHandler = component.backHandler,
			onBack = component::onBack,
		)
	}

	DialogUi(component)

	SetMultiPane(component)
}

@Composable
private fun DialogUi(component: StoryEditor) {
	val dialogState by component.dialogState.subscribeAsState()

	when (val dest = dialogState.child?.instance) {
		is StoryEditor.ChildDestination.DialogDestination.OutlineDestination -> {
			OutlineOverviewUi(dest.component)
		}

		is StoryEditor.ChildDestination.DialogDestination.None -> {}
		null -> {}
	}
}

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
	state: ChildStack<*, StoryEditor.ChildDestination.List>,
	snackbarHostState: RootSnackbarHostState,
	modifier: Modifier,
	isMultiPane: Boolean,
) {
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
					inSplitPane = isMultiPane,
				)

			is StoryEditor.ChildDestination.List.None -> Box {}
		}
	}
}

@Composable
private fun DetailsPane(
	state: ChildStack<*, StoryEditor.ChildDestination.Detail>,
	isMultiPane: Boolean,
	snackbarHostState: RootSnackbarHostState,
	modifier: Modifier,
	backHandler: BackHandler,
	onBack: () -> Unit,
) {
	Children(
		stack = state,
		modifier = modifier,
		animation = predictiveBackAnimation(
			backHandler = backHandler,
			fallbackAnimation = stackAnimation { _ -> fade() },
			onBack = onBack,
		),
	) {
		when (val child = it.instance) {
			is StoryEditor.ChildDestination.Detail.None -> {
				EmptySceneEditor(isMultiPane)
			}

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

			is StoryEditor.ChildDestination.Detail.ReaderDestination -> {
				ReaderModeUi(component = child.component)
			}
		}
	}
}

@Composable
internal fun EmptySceneEditor(isMultipane: Boolean) {
	if (isMultipane) {
		HdPageEmpty(text = Res.string.scene_editor_no_scene_selected.get())
	} else {
		Box(modifier = Modifier.fillMaxSize())
	}
}
