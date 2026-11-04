package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.timeline.TimelineRuleProblem

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

/** The closest client error for a broken shared rule; never [TimeLineEventError.NONE]. */
fun TimelineRuleProblem.toEventError(): TimeLineEventError = when (this) {
	TimelineRuleProblem.SubjectIsReference -> TimeLineEventError.REFERENCES_NOT_ALLOWED
	TimelineRuleProblem.SelfReference -> TimeLineEventError.SELF_REFERENCE
	TimelineRuleProblem.EventHasSubject -> TimeLineEventError.SUBJECT_NOT_ALLOWED
	is TimelineRuleProblem.PlacementNeedsSubject -> TimeLineEventError.SUBJECT_REQUIRED
	TimelineRuleProblem.PlacementIsOwnSubject -> TimeLineEventError.SELF_REFERENCE
	TimelineRuleProblem.PlacementHasReferences -> TimeLineEventError.REFERENCES_NOT_ALLOWED
	TimelineRuleProblem.PlacementHasContent -> TimeLineEventError.CONTENT_NOT_ALLOWED
	TimelineRuleProblem.PlacementHasTags -> TimeLineEventError.TAGS_NOT_ALLOWED
	TimelineRuleProblem.LaneBlank,
	TimelineRuleProblem.LaneTooLong -> TimeLineEventError.LANE_INVALID
	TimelineRuleProblem.TitleTooLong,
	TimelineRuleProblem.CachedTitleTooLong -> TimeLineEventError.TITLE_TOO_LONG
	TimelineRuleProblem.LabelTooLong -> TimeLineEventError.LABEL_TOO_LONG
	TimelineRuleProblem.TagTooLong -> TimeLineEventError.TAG_TOO_LONG
	is TimelineRuleProblem.TooManyRungs,
	is TimelineRuleProblem.BlankUnitName,
	is TimelineRuleProblem.UnitNameTooLong -> TimeLineEventError.DATE_INVALID
	TimelineRuleProblem.TimelineIdInvalid,
	TimelineRuleProblem.MainSpelledOut -> TimeLineEventError.TIMELINE_NOT_FOUND
}
