package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

	LaunchedEffect(Unit) {
		textEditorState.textState.editOperations.collect { operation ->
			component.onContentChanged(ComposeRichText(markdownExtension))
		}
	}

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
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
					.fillMaxHeight()
					.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH),
			)
		}
	}
}
