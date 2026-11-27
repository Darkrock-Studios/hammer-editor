package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
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

	// Two caches claiming one folder at once would each clear the other's file.
	private val claimLock = reentrantLock()

	fun projectsCacheRoot(): Path = getCacheDirectory().toPath() / PROJECTS_DIRECTORY

	fun projectCacheDirectory(fileSystem: FileSystem, projectDef: ProjectDef): Path =
		projectCacheDirectory(fileSystem, projectDef.name, projectDef.path)

	private fun projectCacheDirectory(fileSystem: FileSystem, name: String, path: HPath): Path =
		projectsCacheRoot() / cacheId(fileSystem, name, path)

	/**
	 * Deletes every cache of the project named [name] at [path], as when its content is replaced.
	 * Throws when they could not all be deleted, since what is left would pass as current.
	 */
	fun clear(fileSystem: FileSystem, name: String, path: HPath) {
		val dir = projectCacheDirectory(fileSystem, name, path)
		requireWithinRoot(dir)
		fileSystem.deleteRecursively(dir)
	}

	/** [name] in [dir], or null when it is missing or [dir] holds another project's caches. */
	fun fileToRead(fileSystem: FileSystem, dir: Path, projectDef: ProjectDef, name: String): Path? {
		val file = dir / name
		if (!fileSystem.exists(file) || owner(fileSystem, dir) != projectDef.id.id) return null
		return file
	}

	/** [name] in [dir], made ready to write: another project's caches there are cleared first. */
	fun fileToWrite(fileSystem: FileSystem, dir: Path, projectDef: ProjectDef, name: String): Path {
		requireWithinRoot(dir)
		claimLock.withLock {
			fileSystem.createDirectories(dir)
			if (owner(fileSystem, dir) != projectDef.id.id) {
				fileSystem.list(dir).forEach { fileSystem.deleteRecursively(it) }
				fileSystem.write(dir / OWNER_FILENAME) { writeUtf8(projectDef.id.id) }
			}
		}
		return dir / name
	}

	/** Deletes [name] in [dir]. Returns whether there was one to delete. */
	fun deleteFile(fileSystem: FileSystem, dir: Path, name: String): Boolean {
		requireWithinRoot(dir)
		val file = dir / name
		if (!fileSystem.exists(file)) return false
		fileSystem.delete(file)
		return true
	}

	private fun requireWithinRoot(dir: Path) {
		require(dir.isWithin(projectsCacheRoot())) { "Refusing a cache folder outside the cache root: $dir" }
	}

	private fun owner(fileSystem: FileSystem, dir: Path): String? = try {
		fileSystem.read(dir / OWNER_FILENAME) { readUtf8() }
	} catch (_: IOException) {
		null
	}

	internal fun cacheId(fileSystem: FileSystem, projectDef: ProjectDef): String =
		cacheId(fileSystem, projectDef.name, projectDef.path)

	private fun cacheId(fileSystem: FileSystem, name: String, path: HPath): String {
		val pathHash = canonicalPath(fileSystem, path).encodeUtf8().sha256().hex().take(PATH_HASH_LENGTH)
		return "${shortName(name)}-$pathHash"
	}

	/** [projectPath] as the file system resolves it, so two spellings of one folder hash alike. */
	private fun canonicalPath(fileSystem: FileSystem, projectPath: HPath): String {
		val path = projectPath.toOkioPath().normalized()
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
