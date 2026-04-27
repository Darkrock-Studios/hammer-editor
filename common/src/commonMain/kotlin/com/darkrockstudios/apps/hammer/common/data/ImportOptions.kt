package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
enum class ImportFormat { Markdown }

@Serializable
enum class ChapterHeadingLevel { H1, H2 }

@Serializable
data class ImportOptions(
	val format: ImportFormat = ImportFormat.Markdown,
	val chapterHeadingLevel: ChapterHeadingLevel = ChapterHeadingLevel.H1,
	val createChapterGroups: Boolean = false,
)
