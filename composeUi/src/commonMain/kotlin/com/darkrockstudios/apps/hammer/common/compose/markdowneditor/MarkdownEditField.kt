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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalEditorTextStyle
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.compose.LocalRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.markdown.HammerMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateRichTextStyles
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.withParagraphIndent
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.texteditor.EditorLineLimits
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.rememberSpellCheckState
import kotlinx.coroutines.launch

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
	/** The format bar with highlight, task lists, and tables. */
	extendedFormatBar: Boolean = true,
	/**
	 * [EditorLineLimits.Fill] takes the height left in the column, at least [minEditorHeight]; the
	 * others grow with the text, as a field in a scrolling column must, and ignore [minEditorHeight].
	 */
	lineLimits: EditorLineLimits = EditorLineLimits.Fill,
	testTag: String? = null,
	/**
	 * Picks an image to embed at the caret, null when the pick is cancelled. Adds the format bar's
	 * insert image button and its shortcut; only offered inside a project, where images resolve.
	 */
	imagePicker: (suspend () -> ImageDef?)? = null,
) {
	val richTextStyles = LocalRichTextStyles.current
	val imageProvider = LocalImageProvider.current
	val scope = rememberCoroutineScope()

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
		textEditorState.textState.withMarkdown(HammerMarkdownConfiguration, imageProvider)
	}

	val onInsertImage: (() -> Unit)? = if (imagePicker != null && imageProvider != null) {
		{
			scope.launch {
				val image = imagePicker() ?: return@launch
				insertImageBlock(textEditorState.textState, imageProvider, image.id, image.name)
			}
		}
	} else {
		null
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
			modifier.markdownFormatShortcuts(markdownExtension, onInsertImage)
		} else {
			modifier
		}
	) {
		if (enabled && showFormatBar) {
			MarkdownFormatBar(
				markdownState = markdownExtension,
				extended = extendedFormatBar,
				onInsertImage = onInsertImage,
			)
		}
		SpellCheckingTextEditor(
			state = textEditorState,
			enabled = enabled,
			autoFocus = autoFocus,
			style = rememberTextEditorStyle(
				textStyle = LocalEditorTextStyle.current.withParagraphIndent()
			),
			contentPadding = contentPadding,
			lineLimits = lineLimits,
			modifier = Modifier
				.fillMaxWidth()
				.then(if (lineLimits == EditorLineLimits.Fill) Modifier.weight(1f).heightIn(min = minEditorHeight) else Modifier)
				.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
		)
	}
}
