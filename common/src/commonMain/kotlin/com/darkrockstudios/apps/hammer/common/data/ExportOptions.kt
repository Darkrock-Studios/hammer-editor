package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat { Markdown, Epub, Pdf }

@Serializable
data class ExportOptions(
	val treatTopLevelAsChapters: Boolean = true,
	val format: ExportFormat = ExportFormat.Markdown,
)
