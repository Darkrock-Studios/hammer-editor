package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneDeleteDialog
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.markdown.withMarkdown
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
	val markdownConfig = LocalMarkdownConfig.current

	val textEditorState = rememberSpellCheckState(
		spellChecker = state.spellChecker.toEditorSpellChecker(),
		initialText = getInitialEditorContent(state.sceneBuffer?.content, markdownConfig),
		enableSpellChecking = state.spellCheckingEnabled
	)
	val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

	LaunchedEffect(markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}

	LaunchedEffect(lastForceUpdate, state.sceneBuffer) {
		// Only update if the buffer exists and it's from an external source
		state.sceneBuffer?.let { buffer ->
			if (buffer.source != UpdateSource.Editor) {
				val newContent = getInitialEditorContent(buffer.content, markdownConfig)
				textEditorState.textState.setText(newContent)
			}
		}
	}

	LaunchedEffect(Unit) {
		textEditorState.textState.editOperations.collect { _ ->
			component.onContentChanged(ComposeRichText(markdownExtension))
		}
	}

	Toaster(component, rootSnackbar)

	BoxWithConstraints(modifier = modifier) {
		val boxWithConstraintsScope = this

		Column(modifier = Modifier.fillMaxHeight()) {
			EditorTopBar(component, rootSnackbar)

			EditorToolBar(
				markdownState = markdownExtension,
				decreaseTextSize = component::decreaseTextSize,
				increaseTextSize = component::increaseTextSize,
				resetTextSize = component::resetTextSize,
			)

			Row(
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.Center
			) {
				SpellCheckingTextEditor(
					state = textEditorState,
					contentPadding = PaddingValues(Ui.Padding.XL),
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
						.fillMaxHeight()
						.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH),
				)

				HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

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
					modifier = Modifier.wrapContentWidth()
						.widthIn(max = SCENE_METADATA_MAX_WIDTH)
						.fillMaxHeight(),
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