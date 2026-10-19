package com.darkrockstudios.apps.hammer.common.data.export

/** Export formats that can change while the app runs, such as those of plugins installed from Settings. */
interface ExporterSource {
	/** The formats, less those of the plugins in [disabledPlugins]. */
	fun exporters(disabledPlugins: Set<String> = emptySet()): List<StoryExporter>
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
		get() = exporters(emptySet())

	/** The formats a project can export to: [exporters] less those of the plugins it turned off. */
	fun exporters(disabledPlugins: Set<String>): List<StoryExporter> =
		builtInStoryExporters + (contributed + sources.flatMap { it.exporters(disabledPlugins) }).sortedBy { it.formatId }

	fun isRegistered(formatId: String, disabledPlugins: Set<String> = emptySet()): Boolean =
		exporters(disabledPlugins).any { it.formatId == formatId }

	fun forFormat(formatId: String, disabledPlugins: Set<String> = emptySet()): StoryExporter =
		exporters(disabledPlugins).firstOrNull { it.formatId == formatId }
			?: throw IllegalArgumentException("Unknown export format '$formatId'")
}
