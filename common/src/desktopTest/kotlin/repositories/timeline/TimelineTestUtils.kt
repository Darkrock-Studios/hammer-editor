package repositories.timeline

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem

fun fakeEvents(): List<TimeLineEvent> {
	return (0 until 10).map { i ->
		TimeLineEvent(
			id = i,
			order = i,
			date = if (i % 2 == 0) "First date" else null,
			content = "Event $i"
		)
	}
}

fun writeEventsToDisk(
	projectDef: ProjectDef,
	events: List<TimeLineEvent>,
	ffs: FakeFileSystem,
	json: Json,
) {
	val timeline = TimeLineContainer(events)
	val serialized = json.encodeToString(TimeLineContainer.serializer(), timeline)

	val file = TimeLineRepositoryOkio.getTimelineFile(projectDef).toOkioPath()
	ffs.createDirectories(file.parent!!)

	ffs.write(file) {
		writeUtf8(serialized)
	}
}