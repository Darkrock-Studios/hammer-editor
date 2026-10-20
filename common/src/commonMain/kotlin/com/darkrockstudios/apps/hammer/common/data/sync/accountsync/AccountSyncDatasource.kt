package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.common.data.ProjectsDirectory
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.component.KoinComponent

/** Reads and writes the account sync file, `sync.json` in the projects directory. */
class AccountSyncDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val projectsDirectory: ProjectsDirectory,
) : KoinComponent {
	private val ioDispatcher = injectIoDispatcherNow()

	fun getSyncDataPath(): HPath = (projectsDirectory.current().toOkioPath() / SYNC_FILE_NAME).toHPath()

	/** A missing or corrupt file is replaced with a fresh one. */
	suspend fun load(): ProjectsSynchronizationData = withContext(ioDispatcher) {
		val path = getSyncDataPath().toOkioPath()
		val loaded = if (fileSystem.exists(path)) {
			try {
				fileSystem.read(path) { json.decodeFromString<ProjectsSynchronizationData>(readUtf8()) }
			} catch (e: SerializationException) {
				Napier.w("Corrupt account sync.json; starting fresh (${e.message})")
				null
			} catch (e: IllegalArgumentException) {
				Napier.w("Corrupt account sync.json; starting fresh (${e.message})")
				null
			}
		} else {
			null
		}
		loaded ?: ProjectsSynchronizationData().also { writeFile(it) }
	}

	suspend fun save(data: ProjectsSynchronizationData): Unit = withContext(ioDispatcher) {
		writeFile(data)
	}

	suspend fun update(
		action: (ProjectsSynchronizationData) -> ProjectsSynchronizationData,
	): ProjectsSynchronizationData {
		val updated = action(load())
		save(updated)
		return updated
	}

	suspend fun delete(): Unit = withContext(ioDispatcher) {
		fileSystem.delete(getSyncDataPath().toOkioPath(), mustExist = false)
	}

	private fun writeFile(data: ProjectsSynchronizationData) {
		val path = getSyncDataPath().toOkioPath()
		path.parent?.let(fileSystem::createDirectories)
		fileSystem.write(path) {
			writeUtf8(json.encodeToString(ProjectsSynchronizationData.serializer(), data))
		}
	}

	companion object {
		const val SYNC_FILE_NAME = "sync.json"
	}
}
