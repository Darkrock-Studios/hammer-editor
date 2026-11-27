package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import io.github.aakira.napier.Napier
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Where a project's caches live. Projects in different groups can share a name, so the folder is the
 * name followed by a hash of the project's path. A project replaced at the same path lands in the same
 * folder, so the folder records the id of the project that owns it, and a cache is only read by that
 * project.
 */
object ProjectCachePaths {
	private const val PROJECTS_DIRECTORY = "projects"
	private const val OWNER_FILENAME = "project-id"
	private const val PATH_HASH_LENGTH = 16

	// At 4 UTF-8 bytes a character, with the hash, this stays well under the common 255-byte limit
	// on a file name, which a project's own name can come close to.
	private const val MAX_NAME_CHARS = 48

	fun projectsCacheRoot(): Path = getCacheDirectory().toPath() / PROJECTS_DIRECTORY

	fun projectCacheDirectory(fileSystem: FileSystem, projectDef: ProjectDef): Path =
		projectsCacheRoot() / cacheId(fileSystem, projectDef)

	/** [name] in [dir], or null when it is missing or [dir] holds another project's caches. */
	fun fileToRead(fileSystem: FileSystem, dir: Path, projectDef: ProjectDef, name: String): Path? {
		val file = dir / name
		if (!fileSystem.exists(file) || owner(fileSystem, dir) != projectDef.id.id) return null
		return file
	}

	/** [name] in [dir], made ready to write: another project's caches there are cleared first. */
	fun fileToWrite(fileSystem: FileSystem, dir: Path, projectDef: ProjectDef, name: String): Path {
		require(dir.isWithin(projectsCacheRoot())) { "Refusing to write a cache outside the cache root: $dir" }
		fileSystem.createDirectories(dir)
		if (owner(fileSystem, dir) != projectDef.id.id) {
			fileSystem.list(dir).forEach { fileSystem.deleteRecursively(it) }
			fileSystem.write(dir / OWNER_FILENAME) { writeUtf8(projectDef.id.id) }
		}
		return dir / name
	}

	private fun owner(fileSystem: FileSystem, dir: Path): String? = try {
		fileSystem.read(dir / OWNER_FILENAME) { readUtf8() }
	} catch (_: IOException) {
		null
	}

	internal fun cacheId(fileSystem: FileSystem, projectDef: ProjectDef): String {
		val pathHash = canonicalPath(fileSystem, projectDef).encodeUtf8().sha256().hex().take(PATH_HASH_LENGTH)
		return "${shortName(projectDef.name)}-$pathHash"
	}

	/** The project's path as the file system resolves it, so two spellings of one folder hash alike. */
	private fun canonicalPath(fileSystem: FileSystem, projectDef: ProjectDef): String {
		val path = projectDef.path.toOkioPath().normalized()
		val canonical = try {
			fileSystem.canonicalize(path)
		} catch (_: IOException) {
			path
		}
		return canonical.toString()
	}

	private fun shortName(name: String): String {
		val encoded = ProjectsRepository.encodeForFilename(name)
		if (encoded.length <= MAX_NAME_CHARS) return encoded
		val cut = encoded.take(MAX_NAME_CHARS)
		return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
	}

	/**
	 * Deletes every cache folder that none of [projects] owns: those of projects since renamed, moved,
	 * or deleted. Returns how many it deleted.
	 */
	fun removeOrphans(fileSystem: FileSystem, projects: List<ProjectDef>): Int {
		val dirs = fileSystem.listOrNull(projectsCacheRoot())
			?.filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
		if (dirs.isNullOrEmpty()) return 0

		val owned = projects.map { cacheId(fileSystem, it) }.toSet()
		return dirs.filter { it.name !in owned }.count { dir ->
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
