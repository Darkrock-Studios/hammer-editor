package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
enum class ImportFormat { Markdown, Rtf }

@Serializable
enum class ChapterHeadingLevel { Auto, H1, H2 }

/** How an RTF document is divided into scenes. */
@Serializable
enum class RtfSplitStrategy { Formatting, Pattern, SingleScene }

const val DEFAULT_RTF_CHAPTER_PATTERN = "(?i)^(chapter|part|prologue|epilogue)\\b"

@Serializable
data class ImportOptions(
	val format: ImportFormat = ImportFormat.Markdown,
	val chapterHeadingLevel: ChapterHeadingLevel = ChapterHeadingLevel.Auto,
	val createChapterGroups: Boolean = false,
	val rtfSplitStrategy: RtfSplitStrategy = RtfSplitStrategy.Formatting,
	val rtfChapterPattern: String = DEFAULT_RTF_CHAPTER_PATTERN,
)
