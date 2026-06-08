package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import io.github.aakira.napier.Napier
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/** Reads the cached stats file directly, without opening a ProjectDefScope. */
class ProjectStatisticsCacheReader(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {

	fun loadStatistics(projectDef: ProjectDef): ProjectStatistics? {
		val file = StatisticsCachePaths.statsFile(projectDef)
		if (!fileSystem.exists(file)) return null

		val stats = fileSystem.readTomlOrNull<ProjectStatistics>(file, toml) { e ->
			Napier.d("Failed to read statistics cache for ${projectDef.name}", e)
		} ?: return null

		if (stats.schemaVersion != ProjectStatistics.CURRENT_SCHEMA_VERSION) return null
		return stats
	}

	fun loadTotalWords(projectDef: ProjectDef): Int? = loadStatistics(projectDef)?.totalWords
}
