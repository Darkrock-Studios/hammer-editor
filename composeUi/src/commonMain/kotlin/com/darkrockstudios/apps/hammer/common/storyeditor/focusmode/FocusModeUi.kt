package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusMode
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.EditorToolBar
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.getInitialEditorContent
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.apps.hammer.scene_editor_menu_item_close
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.markdown.withMarkdown
import com.darkrockstudios.texteditor.spellcheck.rememberSpellCheckState

@Composable
fun FocusModeUi(component: FocusMode) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current

	val textEditorState = rememberSpellCheckState(
		spellChecker = state.spellChecker.toEditorSpellChecker(),
		initialText = getInitialEditorContent(state.sceneBuffer?.content, markdownConfig),
		enableSpellChecking = state.spellCheckingEnabled,
	)
	val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

	LaunchedEffect(markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}

	var hasReceivedInitialBuffer by remember { mutableStateOf(false) }

	LaunchedEffect(lastForceUpdate, state.sceneBuffer) {
		state.sceneBuffer?.let { buffer ->
			// Always update on first buffer (initial load), or when external update occurs
			if (!hasReceivedInitialBuffer || buffer.source != UpdateSource.Editor) {
				val newContent = getInitialEditorContent(buffer.content, markdownConfig)
				textEditorState.textState.setText(newContent)
				hasReceivedInitialBuffer = true
			}
		}
	}

	LaunchedEffect(hasReceivedInitialBuffer) {
		// Only start collecting edit operations after the initial buffer has been loaded
		// to prevent empty content from being sent to the buffer before it's populated
		if (hasReceivedInitialBuffer) {
			textEditorState.textState.editOperations.collect { operation ->
				component.onContentChanged(ComposeRichText(markdownExtension))
			}
		}
	}

	if (state.isLoading) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			CircularProgressIndicator()
		}
	} else {
		Column(modifier = Modifier.fillMaxSize()) {
			Row(
				modifier = Modifier
					.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				EditorToolBar(
					markdownState = markdownExtension,
					decreaseTextSize = component::decreaseTextSize,
					increaseTextSize = component::increaseTextSize,
					resetTextSize = component::resetTextSize,
					modifier = Modifier.weight(1f),
				)

				IconButton(
					onClick = component::dismiss,
					modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
				) {
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.scene_editor_menu_item_close.get(),
						tint = MaterialTheme.colorScheme.onBackground
					)
				}
			}

			Row(
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.Center
			) {
				SpellCheckingTextEditor(
					state = textEditorState,
					contentPadding = PaddingValues(Ui.Padding.XL),
					enabled = hasReceivedInitialBuffer,
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
						.fillMaxHeight()
						.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH),
				)
			}
		}
	}
}
