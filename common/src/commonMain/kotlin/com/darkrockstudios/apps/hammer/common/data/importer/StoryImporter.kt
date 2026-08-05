package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.countWords
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Above this, a scene reads as a failed chapter split rather than a long chapter. */
const val LARGE_SCENE_WORD_COUNT = 10_000

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

	/** Scenes at or over [LARGE_SCENE_WORD_COUNT], in document order. */
	val oversizedScenes: List<PreviewItem.Scene>
		get() = buildList {
			items.forEach { item ->
				when (item) {
					is PreviewItem.Scene -> if (item.isOversized) add(item)
					is PreviewItem.Group -> item.scenes.filterTo(this) { it.isOversized }
				}
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
	) : PreviewItem {
		@Transient
		val wordCount: Int = countWords(markdown)

		val isOversized: Boolean get() = wordCount >= LARGE_SCENE_WORD_COUNT
	}

	@Serializable
	data class Group(
		override val name: String,
		val scenes: List<Scene>,
	) : PreviewItem
}
