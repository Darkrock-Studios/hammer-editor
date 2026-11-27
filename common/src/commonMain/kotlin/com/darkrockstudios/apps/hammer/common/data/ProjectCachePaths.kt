package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import io.github.aakira.napier.Napier
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
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
		val path = projectDef.path.path.toPath(normalize = true).toString()
		val pathHash = path.encodeUtf8().sha256().hex().take(PATH_HASH_LENGTH)
		return "${ProjectsRepository.encodeForFilename(projectDef.name)}-$pathHash"
	}

	/**
	 * Deletes every cache folder that none of [projects] owns: those of projects since renamed, moved,
	 * or deleted. Returns how many it deleted.
	 */
	fun removeOrphans(fileSystem: FileSystem, projects: List<ProjectDef>): Int {
		val owned = projects.map(::cacheId).toSet()
		val orphans = fileSystem.listOrNull(projectsCacheRoot()).orEmpty().filter { dir ->
			dir.name !in owned && fileSystem.metadataOrNull(dir)?.isDirectory == true
		}
		return orphans.count { dir ->
			try {
				fileSystem.deleteRecursively(dir)
				true
			} catch (e: IOException) {
				Napier.w("Failed to delete orphaned project cache $dir", e)
				false
			}
		}
	}
}
