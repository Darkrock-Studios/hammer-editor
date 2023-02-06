package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileNotFoundException
import okio.FileSystem

class TimeLineRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	private val fileSystem: FileSystem,
	//private val toml: Toml,
	private val json: Json,
) : TimeLineRepository(projectDef, idRepository) {
	override suspend fun loadTimeline(): TimeLineContainer {
		val path = getTimelineFile()
		return loadTimeline(path, fileSystem, json)
	}

	override fun storeTimeline(timeLine: TimeLineContainer) {
		scope.launch {
			val path = getTimelineFile().toOkioPath()
			fileSystem.write(path) {
				val timeLineToml = json.encodeToString(timeLine)
				writeUtf8(timeLineToml)
			}

			_timelineFlow.emit(timeLine)
		}
	}

	override fun getTimelineFile(): HPath {
		val directory = getTimelineDir(projectDef).toOkioPath()

		if (!fileSystem.exists(directory)) {
			fileSystem.createDirectory(directory)
		}
		return getTimelineFile(projectDef)
	}

	companion object {
		fun getTimelineDir(projectDef: ProjectDef): HPath {
			return (projectDef.path.toOkioPath() / TIMELINE_DIRECTORY).toHPath()
		}

		fun getTimelineFile(projectDef: ProjectDef): HPath {
			return (getTimelineDir(projectDef).toOkioPath() / TIMELINE_FILENAME).toHPath()
		}

		fun loadTimeline(hpath: HPath, fileSystem: FileSystem, json: Json): TimeLineContainer {
			val path = hpath.toOkioPath()
			return if (fileSystem.exists(path)) {
				try {
					fileSystem.read(path) {
						val timelineToml = readUtf8()
						if (timelineToml.isNotBlank()) {
							json.decodeFromString(timelineToml)
						} else {
							TimeLineContainer(emptyList())
						}
					}
				} catch (e: FileNotFoundException) {
					TimeLineContainer(
						events = emptyList()
					)
				}
			} else {
				TimeLineContainer(
					events = emptyList()
				)
			}
		}
	}
}