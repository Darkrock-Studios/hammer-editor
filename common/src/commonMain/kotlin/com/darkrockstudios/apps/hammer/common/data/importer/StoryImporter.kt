package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import kotlinx.serialization.Serializable

interface StoryImporter {
	val format: ImportFormat

	fun preview(
		sourceName: String,
		content: ByteArray,
		options: ImportOptions,
	): ImportPreview
}

@Serializable
data class ImportPreview(
	val items: List<PreviewItem>,
	/** A story title detected during import (e.g. a leading Markdown `# Title`), if any. */
	val title: String? = null,
) {
	val isEmpty: Boolean get() = items.isEmpty()

	val totalScenes: Int
		get() = items.sumOf { item ->
			when (item) {
				is PreviewItem.Scene -> 1
				is PreviewItem.Group -> item.scenes.size
			}
		}
}

@Serializable
sealed interface PreviewItem {
	val name: String

	@Serializable
	data class Scene(
		override val name: String,
		val markdown: String,
	) : PreviewItem

	@Serializable
	data class Group(
		override val name: String,
		val scenes: List<Scene>,
	) : PreviewItem
}
