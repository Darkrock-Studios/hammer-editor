package com.darkrockstudios.apps.hammer.common.compose.markdown

import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.ParagraphSeparator
import com.darkrockstudios.texteditor.richstyle.ImageBlockSize
import com.darkrockstudios.texteditor.richstyle.ImageLength

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
