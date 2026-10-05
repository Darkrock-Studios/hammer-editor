package com.darkrockstudios.apps.hammer.common.compose.markdown

import com.darkrockstudios.texteditor.markdown.MarkdownConfiguration
import com.darkrockstudios.texteditor.markdown.ParagraphSeparator

/**
 * One editor line per markdown line. The library default, BLANK_LINE, drops the blank lines
 * between paragraphs on import and rewrites single-newline paragraphs on export.
 */
val HammerMarkdownConfiguration = MarkdownConfiguration(paragraphSeparator = ParagraphSeparator.NEWLINE)
