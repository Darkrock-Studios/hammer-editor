package com.darkrockstudios.apps.hammer.common.compose.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.texteditor.RichTextStyles
import com.darkrockstudios.texteditor.TextEditorStyle
import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.MarkdownExtension
import com.darkrockstudios.texteditor.markdown.ParagraphSeparator
import com.darkrockstudios.texteditor.markdown.withMarkdown
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.richstyle.ImageBlockSize
import com.darkrockstudios.texteditor.richstyle.ImageLength
import com.darkrockstudios.texteditor.state.TextEditorState

/**
 * One editor line per markdown line. The library default, BLANK_LINE, drops the blank lines
 * between paragraphs on import and rewrites single-newline paragraphs on export.
 */
val HammerMarkdownConfiguration = MarkdownConfiguration(paragraphSeparator = ParagraphSeparator.NEWLINE)

/**
 * Every embedded image is drawn at the same share of the editor's width, scaled up or down to it
 * with its aspect ratio kept, so a page of mixed photographs reads evenly and a tall one leaves
 * room for the text around it.
 */
val HammerImageBlockSize = ImageBlockSize(maxWidth = ImageLength.Fraction(0.7f), upscale = true)

/**
 * This editor as markdown in [HammerMarkdownConfiguration], its images resolved through
 * [LocalImageProvider]. [richTextStyles] are assigned before the extension is made, and [init]
 * runs once on each new one. A new one is made for a new editor or image provider.
 */
@Composable
fun TextEditorState.rememberHammerMarkdown(
	richTextStyles: RichTextStyles,
	init: MarkdownExtension.() -> Unit = {},
): MarkdownExtension {
	val imageProvider = LocalImageProvider.current
	return remember(this, imageProvider) {
		this.richTextStyles = richTextStyles
		withMarkdown(HammerMarkdownConfiguration, imageProvider).apply(init)
	}
}

/** [rememberTextEditorStyle] with images drawn at [HammerImageBlockSize]. */
@Composable
fun rememberHammerEditorStyle(
	textStyle: TextStyle,
	focusedBorderColor: Color = MaterialTheme.colorScheme.outline,
	unfocusedBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
): TextEditorStyle = rememberTextEditorStyle(
	textStyle = textStyle,
	focusedBorderColor = focusedBorderColor,
	unfocusedBorderColor = unfocusedBorderColor,
	imageSize = HammerImageBlockSize,
)
