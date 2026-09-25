package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import kotlinx.serialization.Serializable

@Serializable
data class ExportOptions(
	val treatTopLevelAsChapters: Boolean = true,
	/** A [com.darkrockstudios.apps.hammer.common.data.export.StoryExporter.formatId]. */
	val format: String = BuiltInExportFormat.EPUB,
	/** Scene ids the export is limited to; null exports the entire story. */
	val sceneIds: Set<Int>? = null,
)
