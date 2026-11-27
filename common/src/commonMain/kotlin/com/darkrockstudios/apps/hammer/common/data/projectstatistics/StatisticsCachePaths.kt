package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import okio.Path

internal object StatisticsCachePaths {
	const val PROJECTS_DIRECTORY = ProjectCachePaths.PROJECTS_DIRECTORY
	const val FILENAME = "stats.toml"

	fun projectsCacheRoot(): Path = ProjectCachePaths.projectsCacheRoot()

	fun projectCacheDirectory(projectDef: ProjectDef): Path = ProjectCachePaths.projectCacheDirectory(projectDef)

	fun statsFile(projectDef: ProjectDef): Path =
		projectCacheDirectory(projectDef) / FILENAME
}
