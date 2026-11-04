package com.darkrockstudios.apps.hammer.base.timeline

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

object TimelineLimits {
	const val MAX_TITLE = 256
	const val MAX_LABEL = 1024
	const val MAX_LANE = 64
	const val MAX_LANES = 5
	const val MAX_TAG = 32
	const val MAX_RUNGS = 8
	const val MAX_UNIT_NAME = 32
	const val MAX_TIMELINE_ID = 32
	const val MAX_TIMELINE_NAME = 64
}

/** Non-blank, at most [TimelineLimits.MAX_TIMELINE_ID] characters, and only `[A-Za-z0-9_-]`. */
fun isValidTimelineId(id: String): Boolean =
	id.isNotBlank() &&
		id.length <= TimelineLimits.MAX_TIMELINE_ID &&
		id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

enum class TimelineDateField {
	START,
	END,
}

/** A broken [TimelineEntityRules] rule. [message] is English, for logs and API error bodies. */
sealed interface TimelineRuleProblem {
	val message: String

	data object SubjectIsReference : TimelineRuleProblem {
		override val message = "the subject cannot also be a reference"
	}

	data object SelfReference : TimelineRuleProblem {
		override val message = "an event cannot reference itself"
	}

	data object EventHasSubject : TimelineRuleProblem {
		override val message = "an event cannot have a subject"
	}

	data class PlacementNeedsSubject(val kind: TimelineKind) : TimelineRuleProblem {
		override val message get() = "a ${kind.name.lowercase()} placement needs a subject"
	}

	data object PlacementIsOwnSubject : TimelineRuleProblem {
		override val message = "a placement cannot be its own subject"
	}

	data object PlacementHasReferences : TimelineRuleProblem {
		override val message = "a placement cannot have references"
	}

	data object PlacementHasContent : TimelineRuleProblem {
		override val message = "a placement cannot have content"
	}

	data object PlacementHasTags : TimelineRuleProblem {
		override val message = "a placement cannot have tags"
	}

	data object LaneBlank : TimelineRuleProblem {
		override val message = "lane is blank"
	}

	data object LaneTooLong : TimelineRuleProblem {
		override val message = "lane is longer than ${TimelineLimits.MAX_LANE}"
	}

	data object TitleTooLong : TimelineRuleProblem {
		override val message = "title is longer than ${TimelineLimits.MAX_TITLE}"
	}

	data object CachedTitleTooLong : TimelineRuleProblem {
		override val message = "cachedTitle is longer than ${TimelineLimits.MAX_TITLE}"
	}

	data object LabelTooLong : TimelineRuleProblem {
		override val message = "label is longer than ${TimelineLimits.MAX_LABEL}"
	}

	data object TagTooLong : TimelineRuleProblem {
		override val message = "a tag is longer than ${TimelineLimits.MAX_TAG}"
	}

	data class TooManyRungs(val field: TimelineDateField) : TimelineRuleProblem {
		override val message get() = "${field.name.lowercase()} has more than ${TimelineLimits.MAX_RUNGS} rungs"
	}

	data class BlankUnitName(val field: TimelineDateField) : TimelineRuleProblem {
		override val message get() = "${field.name.lowercase()} has a blank unit name"
	}

	data class UnitNameTooLong(val field: TimelineDateField) : TimelineRuleProblem {
		override val message get() =
			"${field.name.lowercase()} has a unit name longer than ${TimelineLimits.MAX_UNIT_NAME}"
	}

	data object TimelineIdInvalid : TimelineRuleProblem {
		override val message =
			"timeline must be 1 to ${TimelineLimits.MAX_TIMELINE_ID} characters of A-Z, a-z, 0-9, _ or -"
	}

	/** Main is stored as a null timeline, never as its id. */
	data object MainSpelledOut : TimelineRuleProblem {
		override val message = "timeline is \"${TimelineDef.MAIN_ID}\"; main is written as no timeline"
	}
}

/**
 * Shape rules for a timeline entity that hold without looking at the rest of the project. Shared by
 * the server's upload validation and the client's conflict validation.
 */
object TimelineEntityRules {
	/** The first broken rule, or null when [entity] is well formed. */
	fun problem(entity: ApiProjectEntity.TimelineEventEntity): TimelineRuleProblem? {
		val subject = entity.subject
		if (subject != null && subject in entity.references) return TimelineRuleProblem.SubjectIsReference
		if (entity.id in entity.references) return TimelineRuleProblem.SelfReference

		if (entity.kind == TimelineKind.EVENT) {
			if (subject != null) return TimelineRuleProblem.EventHasSubject
		} else {
			if (subject == null) return TimelineRuleProblem.PlacementNeedsSubject(entity.kind)
			if (subject == entity.id) return TimelineRuleProblem.PlacementIsOwnSubject
			if (entity.references.isNotEmpty()) return TimelineRuleProblem.PlacementHasReferences
			if (entity.content.isNotEmpty()) return TimelineRuleProblem.PlacementHasContent
			if (entity.tags.isNotEmpty()) return TimelineRuleProblem.PlacementHasTags
		}

		entity.lane?.let { lane ->
			if (lane.isBlank()) return TimelineRuleProblem.LaneBlank
			if (lane.length > TimelineLimits.MAX_LANE) return TimelineRuleProblem.LaneTooLong
		}
		if ((entity.title?.length ?: 0) > TimelineLimits.MAX_TITLE) return TimelineRuleProblem.TitleTooLong
		if ((entity.cachedTitle?.length ?: 0) > TimelineLimits.MAX_TITLE) {
			return TimelineRuleProblem.CachedTitleTooLong
		}
		if ((entity.label?.length ?: 0) > TimelineLimits.MAX_LABEL) return TimelineRuleProblem.LabelTooLong
		if (entity.tags.any { it.length > TimelineLimits.MAX_TAG }) return TimelineRuleProblem.TagTooLong
		entity.timeline?.let { timeline ->
			if (timeline == TimelineDef.MAIN_ID) return TimelineRuleProblem.MainSpelledOut
			if (!isValidTimelineId(timeline)) return TimelineRuleProblem.TimelineIdInvalid
		}

		dateProblem(TimelineDateField.START, entity.start)?.let { return it }
		dateProblem(TimelineDateField.END, entity.end)?.let { return it }

		return null
	}

	private fun dateProblem(field: TimelineDateField, date: StoryDate?): TimelineRuleProblem? {
		if (date == null) return null
		if (date.rungs.size > TimelineLimits.MAX_RUNGS) return TimelineRuleProblem.TooManyRungs(field)
		date.rungs.keys.forEach { unit ->
			if (unit.isBlank()) return TimelineRuleProblem.BlankUnitName(field)
			if (unit.length > TimelineLimits.MAX_UNIT_NAME) return TimelineRuleProblem.UnitNameTooLong(field)
		}
		return null
	}
}
