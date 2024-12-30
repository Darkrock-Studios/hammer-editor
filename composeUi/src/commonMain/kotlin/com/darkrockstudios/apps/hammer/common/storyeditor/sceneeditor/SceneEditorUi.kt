package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneDeleteDialog
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.rememberSpellCheckState

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneEditorUi(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()

	val textEditorState = rememberSpellCheckState(
		spellChecker = state.spellChecker,
		initialText = getInitialEditorContent(state.sceneBuffer?.content)
	)

	LaunchedEffect(Unit) {
		textEditorState.textState.editOperations.collect { operation ->
			component.onContentChanged(ComposeRichText(textEditorState.textState))
		}
	}

	Toaster(component, rootSnackbar)

	BoxWithConstraints(modifier = modifier) {
		val boxWithConstraintsScope = this

		Column(modifier = Modifier.fillMaxHeight()) {
			EditorTopBar(component, rootSnackbar)

			EditorToolBar(
				state = textEditorState.textState,
				decreaseTextSize = component::decreaseTextSize,
				increaseTextSize = component::increaseTextSize,
				resetTextSize = component::resetTextSize,
			)

			//val verticalScrollState = rememberScrollState(0)
			Row(
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.Center
			) {
				SpellCheckingTextEditor(
					state = textEditorState,
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
						.fillMaxHeight()
						.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH)
						.padding(horizontal = Ui.Padding.XL),
				)

				HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

				/*
				MpScrollBar(
					modifier = Modifier.fillMaxHeight(),
					state = verticalScrollState
				)
				*/
				val remainingWidth = remember(boxWithConstraintsScope.maxWidth) {
					boxWithConstraintsScope.maxWidth - TextEditorDefaults.MAX_WIDTH
				}
				SceneMetadataSidebar(component, remainingWidth)
			}
		}
	}

	SaveDraftDialog(state, component) { message ->
		rootSnackbar.showSnackbar(message)
	}

	if (state.confirmDelete) {
		SceneDeleteDialog(state.sceneItem) { doDelete ->
			if (doDelete) {
				component.doDelete()
			} else {
				component.endDelete()
			}
		}
	}
}

@Composable
private fun SceneMetadataSidebar(component: SceneEditor, remainingWidth: Dp) {
	val state by component.state.subscribeAsState()

	if (remainingWidth >= SCENE_METADATA_MIN_WIDTH) {
		AnimatedVisibility(
			visible = state.showMetadata,
			enter = slideInHorizontally { it } + fadeIn(),
			exit = slideOutHorizontally { it } + fadeOut(),
		) {
			Box(modifier = Modifier.padding(Ui.Padding.L)) {
				SceneMetadataPanelUi(
					component = component.sceneMetadataComponent,
					modifier = Modifier.wrapContentWidth().widthIn(max = SCENE_METADATA_MAX_WIDTH).fillMaxHeight(),
					closeMetadata = component::toggleMetadataVisibility,
				)
			}
		}
	} else {
		if (state.showMetadata) {
			Dialog(onDismissRequest = component::toggleMetadataVisibility) {
				Box(modifier = Modifier.padding(Ui.Padding.L)) {
					SceneMetadataPanelUi(
						component = component.sceneMetadataComponent,
						modifier = Modifier.fillMaxWidth().wrapContentHeight(),
						closeMetadata = component::toggleMetadataVisibility,
					)
				}
			}
		}
	}
}