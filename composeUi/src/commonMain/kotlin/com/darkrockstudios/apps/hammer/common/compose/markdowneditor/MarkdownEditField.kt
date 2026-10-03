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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalEditorTextStyle
import com.darkrockstudios.apps.hammer.common.compose.LocalRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.withParagraphIndent
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
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
	testTag: String? = null,
) {
	val richTextStyles = LocalRichTextStyles.current

	val spellCheckRepository = rememberKoinInject<SpellCheckRepository>()
	val platformSpellChecker by spellCheckRepository.dictionaryFlow.collectAsState(initial = null)

	// rememberSpellCheckState re-scans the whole document whenever this instance changes identity.
	val editorSpellChecker = remember(platformSpellChecker) { platformSpellChecker.toEditorSpellChecker() }

	val textEditorState = rememberSpellCheckState(
		spellChecker = editorSpellChecker,
		initialText = null,
		// The repository only emits a dictionary while spell checking is globally enabled,
		// so a null checker also means "disabled" — clear decorations rather than keep checking.
		enableSpellChecking = enableSpellChecking && platformSpellChecker != null,
	)
	val markdownExtension = remember {
		textEditorState.textState.richTextStyles = richTextStyles
		textEditorState.textState.withMarkdown(HammerMarkdownConfiguration)
	}

	LaunchedEffect(markdownExtension) {
		markdownExtension.importMarkdown(initialMarkdown)
		if (enabled) {
			textEditorState.textState.editOperations.collect {
				onMarkdownChanged(markdownExtension.exportAsMarkdown())
			}
		}
	}

	LaunchedEffect(richTextStyles) {
		textEditorState.textState.updateRichTextStyles(richTextStyles)
	}

	Column(
		modifier = if (enabled) {
			modifier.markdownFormatShortcuts(markdownExtension)
		} else {
			modifier
		}
	) {
		if (enabled && showFormatBar) {
			MarkdownFormatBar(markdownState = markdownExtension)
		}
		SpellCheckingTextEditor(
			state = textEditorState,
			enabled = enabled,
			autoFocus = autoFocus,
			style = rememberTextEditorStyle(
				textStyle = LocalEditorTextStyle.current.withParagraphIndent()
			),
			contentPadding = contentPadding,
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.heightIn(min = minEditorHeight)
				.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
		)
	}
}
