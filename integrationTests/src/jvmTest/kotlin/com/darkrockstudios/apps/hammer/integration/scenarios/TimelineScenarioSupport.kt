package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflict
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.mergeDeletedTimelines
import com.darkrockstudios.apps.hammer.common.data.projectdata.withoutDeleted
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import kotlinx.coroutines.flow.first
import kotlin.test.assertIs
import kotlin.test.assertNotNull

internal fun HeadlessClient.timelineService(): TimelineService = scope.get()

internal suspend fun HeadlessClient.projectData(): ProjectData = scope.get<ProjectDataRepository>().load().data

internal suspend fun HeadlessClient.events(): List<TimeLineEvent> = scope.get<TimeLineRepository>().currentEvents()

internal suspend fun HeadlessClient.eventIdsOn(timeline: String): Set<Int> =
	events().filter { it.timelineId == timeline }.mapTo(mutableSetOf()) { it.id }

internal suspend fun HeadlessClient.orphans(): List<TimeLineEvent> = timelineService().orphansFlow.first()

/** Creates a manual timeline through [TimelineService] and returns its id. */
internal suspend fun HeadlessClient.createTimeline(name: String): String =
	assertIs<TimelineDefResult.Ok>(timelineService().createTimeline(name), "create timeline $name").def.id

/** Creates a plain event on [timeline] through [TimelineService] and returns its id. */
internal suspend fun HeadlessClient.createEventOn(timeline: String, title: String): Int {
	val result = timelineService().createEvent(TimelineEventDraft(title = title, timeline = timeline))
	return assertNotNull(assertIs<TimelineResult.Ok>(result, "create event $title").event).id
}

/** Whether the conflict screen marks the timelines field as conflicting, by its own rule. */
internal fun ProjectDataConflict.showsTimelinesConflict(): Boolean {
	val deleted = mergeDeletedTimelines(local, server)
	return local.timelines.withoutDeleted(deleted) != server.timelines.withoutDeleted(deleted)
}
