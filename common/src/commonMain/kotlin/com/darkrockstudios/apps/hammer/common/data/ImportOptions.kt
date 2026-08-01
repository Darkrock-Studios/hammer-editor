package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
enum class ImportFormat { Markdown, Rtf }

/** How a Markdown document is divided into scenes. */
@Serializable
enum class MarkdownSplitStrategy { Auto, H1, H2, Pattern }

/** How an RTF document is divided into scenes. */
@Serializable
enum class RtfSplitStrategy { Formatting, Pattern, SingleScene }

const val DEFAULT_CHAPTER_PATTERN = "(?i)^(chapter|part|prologue|epilogue)\\b"

@Serializable
data class ImportOptions(
	val format: ImportFormat = ImportFormat.Markdown,
	val markdownSplitStrategy: MarkdownSplitStrategy = MarkdownSplitStrategy.Auto,
	val createChapterGroups: Boolean = false,
	val markdownChapterPattern: String = DEFAULT_CHAPTER_PATTERN,
	val rtfSplitStrategy: RtfSplitStrategy = RtfSplitStrategy.Formatting,
	val rtfChapterPattern: String = DEFAULT_CHAPTER_PATTERN,
)
