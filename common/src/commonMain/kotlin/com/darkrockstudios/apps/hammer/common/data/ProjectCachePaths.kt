package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import okio.Path.Companion.toPath

/**
 * Where a project's caches live. Projects in different groups can share a name, so the folder is the
 * name followed by a hash of the project's path. A project replaced at the same path still lands in
 * the same folder: a cache that can outlive its project must also record which project it is for.
 */
object ProjectCachePaths {
	const val PROJECTS_DIRECTORY = "projects"
	private const val PATH_HASH_LENGTH = 16

	fun projectsCacheRoot(): Path = getCacheDirectory().toPath() / PROJECTS_DIRECTORY

	fun projectCacheDirectory(projectDef: ProjectDef): Path =
		projectsCacheRoot() / cacheId(projectDef)

	fun cacheId(projectDef: ProjectDef): String {
		val pathHash = projectDef.path.path.encodeUtf8().sha256().hex().take(PATH_HASH_LENGTH)
		return "${ProjectsRepository.encodeForFilename(projectDef.name)}-$pathHash"
	}
}
