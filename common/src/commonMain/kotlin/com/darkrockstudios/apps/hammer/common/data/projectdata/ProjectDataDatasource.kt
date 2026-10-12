package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.time.Clock

/**
 * Owns `project_settings.toml`: user data (author, theme, goals, tags, language) that is synced
 * and that the app cannot rebuild. It is kept apart from `project.toml`, which holds local,
 * regenerable metadata that any reader may heal or recreate. User data is never replaced with
 * defaults: an unreadable file is moved aside to `project_settings.toml.corrupt-<epochMillis>`.
 */
class ProjectDataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	fun getPath(): Path = projectDef.path.toOkioPath() / FILENAME

	suspend fun load(): StoredProjectData = withContext(dispatcherIo) {
		readStoredProjectData(projectDef, fileSystem, toml)
	}

	suspend fun save(stored: StoredProjectData): Unit = withContext(dispatcherIo) {
		saveStoredProjectData(projectDef, fileSystem, toml, stored)
	}

	companion object {
		const val FILENAME = "project_settings.toml"
		const val CORRUPT_PREFIX = "$FILENAME.corrupt-"
		const val FILE_HEADER =
			"# Hammer project settings. You may edit this file; keep it valid TOML or Hammer will set it aside."
	}
}

/** [lastSyncedHash] is replayed as `originalHash` on the next upload for conflict detection. */
@Serializable
data class StoredProjectData(
	val data: ProjectData = ProjectData(),
	val lastSyncedHash: String? = null,
)

/**
 * Reads `project_settings.toml` for [projectDef] off the IO dispatcher without
 * opening a per-project Koin scope. Returns `StoredProjectData()` defaults
 * when the file is missing or unreadable so callers don't need to handle
 * I/O errors themselves.
 */
suspend fun loadStoredProjectData(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
): StoredProjectData = withContext(Dispatchers.IO) {
	readStoredProjectData(projectDef, fileSystem, toml)
}

/**
 * Blocking twin of [loadStoredProjectData] for callers that already own their threading (parallel
 * mappers, IO-dispatched code). An existing file that can't be read is moved aside before the
 * defaults are returned, so the next save can't overwrite it.
 */
fun readStoredProjectData(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
): StoredProjectData {
	val path = projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME
	if (!fileSystem.exists(path)) return StoredProjectData()

	var failure: Throwable? = null
	val stored = fileSystem.readTomlOrNull<StoredProjectData>(path, toml) { e -> failure = e }
	if (stored != null) return stored

	setAsideUnreadable(projectDef, fileSystem, path, failure)
	return StoredProjectData()
}

private fun setAsideUnreadable(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	path: Path,
	cause: Throwable?,
) {
	val millis = Clock.System.now().toEpochMilliseconds()
	val target = projectDef.path.toOkioPath() / "${ProjectDataDatasource.CORRUPT_PREFIX}$millis"
	try {
		fileSystem.atomicMove(path, target)
		Napier.e("Unreadable ${ProjectDataDatasource.FILENAME} moved aside: $path -> $target", cause)
	} catch (e: IOException) {
		Napier.e("Unreadable ${ProjectDataDatasource.FILENAME} could not be moved aside: $path", e)
	}
}

/**
 * The single writer for `project_settings.toml`. [ProjectDataDatasource.save] delegates here;
 * scope-less callers (project creation, before a per-project Koin scope exists) use it
 * directly so the on-disk format has exactly one owner.
 */
fun saveStoredProjectData(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
	stored: StoredProjectData,
) {
	val body = toml.encodeToString(StoredProjectData.serializer(), stored)
	fileSystem.write(projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME) {
		writeUtf8(ProjectDataDatasource.FILE_HEADER)
		writeUtf8("\n")
		writeUtf8(body)
	}
}

/**
 * Drops [StoredProjectData.lastSyncedHash] while leaving [StoredProjectData.data] intact, for when
 * the project starts pointing at a different server project and the old baseline no longer
 * describes any agreement. Never creates the file: no file means no baseline to clear.
 */
fun clearProjectDataSyncBaseline(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
) {
	if (!fileSystem.exists(projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME)) return
	val stored = readStoredProjectData(projectDef, fileSystem, toml)
	if (stored.lastSyncedHash == null) return
	saveStoredProjectData(projectDef, fileSystem, toml, stored.copy(lastSyncedHash = null))
}
