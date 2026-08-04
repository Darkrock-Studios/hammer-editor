package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/** Reads the cached stats file via the datasource's scope-less helper, without opening a ProjectDefScope. */
class ProjectStatisticsCacheReader(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {

	fun loadStatistics(projectDef: ProjectDef): ProjectStatistics? {
		val stats = readProjectStatistics(projectDef, fileSystem, toml) ?: return null
		if (stats.schemaVersion != ProjectStatistics.CURRENT_SCHEMA_VERSION) return null
		return stats
	}

	fun loadTotalWords(projectDef: ProjectDef): Int? = loadStatistics(projectDef)?.totalWords
}
