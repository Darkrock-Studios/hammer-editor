package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileNotFoundException
import okio.FileSystem

class TimeLineRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : TimeLineRepository(projectDef, idRepository) {

	override suspend fun loadTimeline(): TimeLineContainer {
		val path = getTimelineFile()
		return loadTimeline(path, fileSystem, toml)
	}

	override suspend fun createEvent(content: String, date: String?, id: Int?, order: Int?): TimeLineEvent {
		val eventId = id ?: idRepository.claimNextId()
		val timeline = timelineFlow.first()

		val event = TimeLineEvent(
			id = eventId,
			order = order ?: timeline.events.size,
			content = content,
			date = date
		)

		val newTimeline = timeline.copy(
			events = timeline.events.toMutableList() + event
		)

		storeTimeline(newTimeline)

		if (id != null) {
			val index = newTimeline.events.indexOf(event)
			markForSynchronization(event, index)
		}

		return event
	}

	override suspend fun updateEvent(event: TimeLineEvent, markForSync: Boolean): Boolean {
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val originalIndex = events.indexOfFirst { it.id == event.id }

		var oldEvent: TimeLineEvent? = null
		if (originalIndex != -1) {
			oldEvent = events[originalIndex]
			events[originalIndex] = event
		} else {
			events.add(event)
		}

		val updatedTimeline = timeline.copy(
			events = events
		)

		correctEventOrder(updatedTimeline)

		if (markForSync) {
			markForSynchronization(oldEvent ?: event, originalIndex)
		}

		return true
	}

	override suspend fun storeTimeline(timeLine: TimeLineContainer) {
		val path = getTimelineFile().toOkioPath()
		fileSystem.write(path) {
			val timeLineToml = toml.encodeToString(timeLine)
			writeUtf8(timeLineToml)
		}

		_timelineFlow.emit(timeLine)
	}

	override suspend fun deleteEvent(event: TimeLineEvent): Boolean {
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val index = events.indexOfFirst { it.id == event.id }
		events.removeAt(index)
		storeTimeline(
			timeline.copy(
				events = events
			)
		)

		projectSynchronizer.recordIdDeletion(event.id)

		return true
	}

	override fun getTimelineFile(): HPath {
		val directory = getTimelineDir(projectDef).toOkioPath()

		if (!fileSystem.exists(directory)) {
			fileSystem.createDirectory(directory)
		}
		return getTimelineFile(projectDef)
	}

	override suspend fun reIdEvent(oldId: Int, newId: Int) {
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val index = events.indexOfFirst { it.id == oldId }
		val oldEvent = events[index]

		val newEvent = oldEvent.copy(
			id = newId
		)

		events[index] = newEvent

		val updatedTimeline = timeline.copy(
			events = events
		)

		storeTimeline(updatedTimeline)
	}

	companion object {
		fun getTimelineDir(projectDef: ProjectDef): HPath {
			return (projectDef.path.toOkioPath() / TIMELINE_DIRECTORY).toHPath()
		}

		fun getTimelineFile(projectDef: ProjectDef): HPath {
			return (getTimelineDir(projectDef).toOkioPath() / TIMELINE_FILENAME).toHPath()
		}

		fun loadTimeline(hpath: HPath, fileSystem: FileSystem, toml: Toml): TimeLineContainer {
			val path = hpath.toOkioPath()
			return if (fileSystem.exists(path)) {
				try {
					fileSystem.read(path) {
						val timelineToml = readUtf8()
						if (timelineToml.isNotBlank()) {
							toml.decodeFromString(timelineToml)
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