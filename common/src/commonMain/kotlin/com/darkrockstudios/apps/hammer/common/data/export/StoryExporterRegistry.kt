package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/** Export formats that can change while the app runs, such as those of plugins installed from Settings. */
interface ExporterSource {
	/** The formats, less those of the plugins in [disabledPlugins]. */
	fun exporters(disabledPlugins: Set<String> = emptySet()): List<StoryExporter>

	/** Emits whenever [exporters] may have changed, starting with an emission on collection. */
	val changes: Flow<Unit>
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

	/** Emits whenever the formats of [sources] may have changed. Never emits without sources. */
	val changes: Flow<Unit> = sources.map { it.changes }.merge()

	/** The formats a project can export to: [exporters] less those of the plugins it turned off. */
	fun exporters(disabledPlugins: Set<String>): List<StoryExporter> =
		builtInStoryExporters + (contributed + sourced(disabledPlugins)).sortedBy { it.formatId }

	/** Null when [formatId] is not available to a project with [disabledPlugins] turned off. */
	fun find(formatId: String, disabledPlugins: Set<String> = emptySet()): StoryExporter? =
		(builtInStoryExporters + contributed).firstOrNull { it.formatId == formatId }
			?: sourced(disabledPlugins).firstOrNull { it.formatId == formatId }

	fun isRegistered(formatId: String, disabledPlugins: Set<String> = emptySet()): Boolean =
		find(formatId, disabledPlugins) != null

	fun forFormat(formatId: String, disabledPlugins: Set<String> = emptySet()): StoryExporter =
		find(formatId, disabledPlugins) ?: throw IllegalArgumentException("Unknown export format '$formatId'")

	/** [formatId]'s exporter, or the default format's when it is not available. */
	fun forFormatOrDefault(formatId: String, disabledPlugins: Set<String> = emptySet()): StoryExporter =
		find(formatId, disabledPlugins) ?: forFormat(ExportOptions().format)

	// Plugin code: one that fails must not take the built-in formats down with it.
	private fun sourced(disabledPlugins: Set<String>): List<StoryExporter> = sources.flatMap { source ->
		try {
			source.exporters(disabledPlugins)
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Export format source failed", e)
			emptyList()
		}
	}
}
