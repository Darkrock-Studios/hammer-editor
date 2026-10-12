package com.darkrockstudios.apps.hammer.common.data.export

/** Every available export format: the built-in ones in menu order, then contributed ones by id. */
class StoryExporterRegistry(contributed: List<StoryExporter>) {
	val exporters: List<StoryExporter> = builtInStoryExporters + contributed.sortedBy { it.formatId }

	private val byId: Map<String, StoryExporter> = exporters.associateBy { it.formatId }

	init {
		require(byId.size == exporters.size) {
			"Duplicate export format ids: ${exporters.groupBy { it.formatId }.filterValues { it.size > 1 }.keys}"
		}
	}

	fun isRegistered(formatId: String): Boolean = formatId in byId

	fun forFormat(formatId: String): StoryExporter =
		byId[formatId] ?: throw IllegalArgumentException("Unknown export format '$formatId'")
}
