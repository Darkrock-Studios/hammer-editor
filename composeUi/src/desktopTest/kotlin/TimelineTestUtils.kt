import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

fun fakeEvents(): List<TimeLineEvent> {
	return (0 until 10).map { i ->
		TimeLineEvent(
			id = i,
			order = i,
			label = if (i % 2 == 0) "First date" else null,
			content = "Event $i"
		)
	}
}