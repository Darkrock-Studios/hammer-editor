package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import okio.FileSystem
import okio.Path

internal object StatisticsCachePaths {
	const val FILENAME = "stats.toml"

	fun statsFile(fileSystem: FileSystem, projectDef: ProjectDef): Path =
		ProjectCachePaths.projectCacheDirectory(fileSystem, projectDef) / FILENAME
}
