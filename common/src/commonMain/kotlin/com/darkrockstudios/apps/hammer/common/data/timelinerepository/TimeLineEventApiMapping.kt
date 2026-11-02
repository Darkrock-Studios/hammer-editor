package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

/** Field for field with no normalisation, so the local hash equals the API hash. */
fun TimeLineEvent.toApiEntity(): ApiProjectEntity.TimelineEventEntity =
	ApiProjectEntity.TimelineEventEntity(
		id = id,
		order = order,
		label = label,
		content = content,
		tags = tags,
		kind = kind,
		subject = subject,
		references = references,
		lane = lane,
		milestone = milestone,
		start = start,
		end = end,
		title = title,
		cachedTitle = cachedTitle,
	)

fun ApiProjectEntity.TimelineEventEntity.toTimeLineEvent(): TimeLineEvent =
	TimeLineEvent(
		id = id,
		order = order,
		kind = kind,
		subject = subject,
		title = title,
		label = label,
		content = content,
		tags = tags,
		references = references,
		lane = lane,
		milestone = milestone,
		start = start,
		end = end,
		cachedTitle = cachedTitle,
	)
