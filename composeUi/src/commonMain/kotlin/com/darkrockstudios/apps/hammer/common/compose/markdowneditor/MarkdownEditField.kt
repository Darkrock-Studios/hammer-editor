package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.texteditor.markdown.toAnnotatedStringFromMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.markdown.withMarkdown
import com.darkrockstudios.texteditor.spellcheck.rememberSpellCheckState

/**
 * Drop-in markdown body editor with spell-check + format bar.
 *
 * `initialMarkdown` is consumed only at first composition — to seed with new content,
 * remount the field (e.g. by gating composition on a non-null state, or by passing a
 * stable `key` to a parent `key()` block).
 */
@Composable
fun MarkdownEditField(
	initialMarkdown: String,
	onMarkdownChanged: (String) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	enableSpellChecking: Boolean = true,
	showFormatBar: Boolean = true,
	autoFocus: Boolean = false,
	contentPadding: PaddingValues = PaddingValues(),
	minEditorHeight: Dp = 200.dp,
) {
	val markdownConfig = LocalMarkdownConfig.current

	val spellCheckRepository = rememberKoinInject<SpellCheckRepository>()
	val platformSpellChecker by spellCheckRepository.dictionaryFlow.collectAsState(initial = null)
	val editorSpellChecker = if (enableSpellChecking) platformSpellChecker.toEditorSpellChecker() else null

	val initialAnnotated = remember {
		initialMarkdown.toAnnotatedStringFromMarkdown(markdownConfig)
	}

	val textEditorState = rememberSpellCheckState(
		spellChecker = editorSpellChecker,
		initialText = initialAnnotated,
		enableSpellChecking = enableSpellChecking,
	)
	val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

	LaunchedEffect(markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}

	LaunchedEffect(enabled) {
		if (enabled) {
			textEditorState.textState.editOperations.collect {
				onMarkdownChanged(markdownExtension.exportAsMarkdown())
			}
		}
	}

	Column(modifier = modifier) {
		if (enabled && showFormatBar) {
			MarkdownFormatBar(markdownState = markdownExtension)
		}
		SpellCheckingTextEditor(
			state = textEditorState,
			enabled = enabled,
			autoFocus = autoFocus,
			style = rememberTextEditorStyle(
				textStyle = TextStyle.Default.copy(
					textIndent = TextIndent(firstLine = 24.sp)
				)
			),
			contentPadding = contentPadding,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = minEditorHeight),
		)
	}
}
