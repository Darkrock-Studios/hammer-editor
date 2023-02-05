package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileNotFoundException
import okio.FileSystem

class TimeLineRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml
) : TimeLineRepository(projectDef, idRepository) {
	override suspend fun loadTimeline(): TimeLineContainer {
		return loadTimeline(projectDef, fileSystem, toml)
	}

	override fun storeTimeline(timeLine: TimeLineContainer) {
		scope.launch {
			val path = getTimelineFile().toOkioPath()
			fileSystem.write(path) {
				val timeLineToml = toml.encodeToString(timeLine)
				writeUtf8(timeLineToml)
			}

			_timelineFlow.emit(timeLine)
		}
	}

	override fun getTimelineFile(): HPath {
		val directory = getTimelineDir(projectDef).toOkioPath()
		fileSystem.createDirectory(directory)

		return getTimelineFile(projectDef)
	}

	companion object {
		fun getTimelineDir(projectDef: ProjectDef): HPath {
			return (projectDef.path.toOkioPath() / TIMELINE_DIRECTORY).toHPath()
		}

		fun getTimelineFile(projectDef: ProjectDef): HPath {
			return (getTimelineDir(projectDef).toOkioPath() / TIMELINE_FILENAME).toHPath()
		}

		fun loadTimeline(projectDef: ProjectDef, fileSystem: FileSystem, toml: Toml): TimeLineContainer {
			val path = getTimelineFile(projectDef).toOkioPath()
			if (fileSystem.exists(path)) {
				try {
					fileSystem.read(path) {
						val timelineToml = readUtf8()
						val timeline: TimeLineContainer = toml.decodeFromString(timelineToml)
						return timeline
					}
				} catch (e: FileNotFoundException) {
					return TimeLineContainer(
						events = emptyList()
					)
				}
			} else {
				return TimeLineContainer(
					events = emptyList()
				)
			}
		}
	}
}