package com.darkrockstudios.apps.hammer.common.data.export

/** Export formats that can change while the app runs, such as those of plugins installed from Settings. */
fun interface ExporterSource {
	fun exporters(): List<StoryExporter>
}

/**
 * Every available export format: the built-in ones in menu order, then the others by id. Formats from
 * [sources] are read afresh on each lookup.
 */
class StoryExporterRegistry(
	private val contributed: List<StoryExporter>,
	private val sources: List<ExporterSource> = emptyList(),
) {
	init {
		val ids = (builtInStoryExporters + contributed).groupBy { it.formatId }
		require(ids.all { it.value.size == 1 }) {
			"Duplicate export format ids: ${ids.filterValues { it.size > 1 }.keys}"
		}
	}

	val exporters: List<StoryExporter>
		get() = builtInStoryExporters + (contributed + sources.flatMap { it.exporters() }).sortedBy { it.formatId }

	fun isRegistered(formatId: String): Boolean = exporters.any { it.formatId == formatId }

	fun forFormat(formatId: String): StoryExporter =
		exporters.firstOrNull { it.formatId == formatId } ?: throw IllegalArgumentException("Unknown export format '$formatId'")
}
