package com.darkrockstudios.apps.hammer.base.timeline

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity

object TimelineLimits {
	const val MAX_TITLE = 256
	const val MAX_LABEL = 1024
	const val MAX_LANE = 64
	const val MAX_TAG = 32
	const val MAX_RUNGS = 8
	const val MAX_UNIT_NAME = 32
}

/**
 * Shape rules for a timeline entity that hold without looking at the rest of the project. Shared by
 * the server's upload validation and the client's conflict validation.
 */
object TimelineEntityRules {
	/** The first broken rule as a short English reason, or null when [entity] is well formed. */
	fun problem(entity: ApiProjectEntity.TimelineEventEntity): String? {
		val subject = entity.subject
		if (subject != null && subject in entity.references) return "the subject cannot also be a reference"
		if (entity.id in entity.references) return "an event cannot reference itself"

		if (entity.kind == TimelineKind.EVENT) {
			if (subject != null) return "an event cannot have a subject"
		} else {
			val kindName = entity.kind.name.lowercase()
			if (subject == null) return "a $kindName placement needs a subject"
			if (subject == entity.id) return "a placement cannot be its own subject"
			if (entity.references.isNotEmpty()) return "a placement cannot have references"
			if (entity.content.isNotEmpty()) return "a placement cannot have content"
			if (entity.tags.isNotEmpty()) return "a placement cannot have tags"
		}

		entity.lane?.let { lane ->
			if (lane.isBlank()) return "lane is blank"
			if (lane.length > TimelineLimits.MAX_LANE) return "lane is longer than ${TimelineLimits.MAX_LANE}"
		}
		if ((entity.title?.length ?: 0) > TimelineLimits.MAX_TITLE) {
			return "title is longer than ${TimelineLimits.MAX_TITLE}"
		}
		if ((entity.cachedTitle?.length ?: 0) > TimelineLimits.MAX_TITLE) {
			return "cachedTitle is longer than ${TimelineLimits.MAX_TITLE}"
		}
		if ((entity.label?.length ?: 0) > TimelineLimits.MAX_LABEL) {
			return "label is longer than ${TimelineLimits.MAX_LABEL}"
		}
		if (entity.tags.any { it.length > TimelineLimits.MAX_TAG }) {
			return "a tag is longer than ${TimelineLimits.MAX_TAG}"
		}

		dateProblem("start", entity.start)?.let { return it }
		dateProblem("end", entity.end)?.let { return it }

		return null
	}

	private fun dateProblem(field: String, date: StoryDate?): String? {
		if (date == null) return null
		if (date.rungs.size > TimelineLimits.MAX_RUNGS) {
			return "$field has more than ${TimelineLimits.MAX_RUNGS} rungs"
		}
		date.rungs.keys.forEach { unit ->
			if (unit.isBlank()) return "$field has a blank unit name"
			if (unit.length > TimelineLimits.MAX_UNIT_NAME) {
				return "$field has a unit name longer than ${TimelineLimits.MAX_UNIT_NAME}"
			}
		}
		return null
	}
}
