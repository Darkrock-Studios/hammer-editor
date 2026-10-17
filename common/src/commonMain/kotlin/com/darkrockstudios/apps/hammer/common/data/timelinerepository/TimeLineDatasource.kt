package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileNotFoundException
import okio.FileSystem
import org.koin.core.component.KoinComponent

class TimeLineDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()

	suspend fun loadTimeline(projectDef: ProjectDef): TimeLineContainer =
		withContext(ioDispatcher) {
			val path = getTimelineFile(projectDef)
			return@withContext loadTimeline(path, fileSystem, toml)
		}

	private suspend fun getTimelineFile(projectDef: ProjectDef): HPath =
		withContext(ioDispatcher) {
			val directory = getTimelineDir(projectDef).toOkioPath()

			if (!fileSystem.exists(directory)) {
				fileSystem.createDirectories(directory)
			}
			return@withContext getTimelineFilePath(projectDef)
		}

	suspend fun storeTimeline(timeLine: TimeLineContainer, projectDef: ProjectDef): Unit =
		withContext(ioDispatcher) {
			val path = getTimelineFile(projectDef).toOkioPath()
			fileSystem.write(path) {
				val timeLineToml = toml.encodeToString(timeLine)
				writeUtf8(timeLineToml)
			}
		}

	companion object {
		private const val TIMELINE_FILENAME = "timeline.toml"
		const val TIMELINE_DIRECTORY = "timeline"

		fun getTimelineDir(projectDef: ProjectDef): HPath {
			return (projectDef.path.toOkioPath() / TIMELINE_DIRECTORY).toHPath()
		}

		fun getTimelineFilePath(projectDef: ProjectDef): HPath {
			return (getTimelineDir(projectDef).toOkioPath() / TIMELINE_FILENAME).toHPath()
		}

		/** Missing or corrupt file means an empty timeline. */
		fun loadTimeline(hpath: HPath, fileSystem: FileSystem, toml: Toml): TimeLineContainer =
			loadTimelineOrNull(hpath, fileSystem, toml) ?: TimeLineContainer(emptyList())

		/**
		 * Null when the file exists but can't be decoded, for callers that must not mistake
		 * an unreadable timeline for an empty one. A missing or blank file is an empty timeline.
		 */
		@Suppress("SwallowedException")
		fun loadTimelineOrNull(hpath: HPath, fileSystem: FileSystem, toml: Toml): TimeLineContainer? {
			val path = hpath.toOkioPath()
			if (!fileSystem.exists(path)) return TimeLineContainer(emptyList())

			return try {
				fileSystem.read(path) {
					val timelineToml = readUtf8()
					if (timelineToml.isNotBlank()) {
						toml.decodeFromString(timelineToml)
					} else {
						TimeLineContainer(emptyList())
					}
				}
			} catch (e: FileNotFoundException) {
				TimeLineContainer(emptyList())
			} catch (e: SerializationException) {
				null
			} catch (e: IllegalArgumentException) {
				null
			} catch (e: IllegalStateException) {
				null
			}
		}
	}
}