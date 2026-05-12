package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import okio.Path
import okio.Path.Companion.toPath

internal object StatisticsCachePaths {
	const val PROJECTS_DIRECTORY = "projects"
	const val FILENAME = "stats.toml"

	fun projectCacheDirectory(projectDef: ProjectDef): Path =
		getCacheDirectory().toPath() / PROJECTS_DIRECTORY / projectDef.name

	fun statsFile(projectDef: ProjectDef): Path =
		projectCacheDirectory(projectDef) / FILENAME
}
