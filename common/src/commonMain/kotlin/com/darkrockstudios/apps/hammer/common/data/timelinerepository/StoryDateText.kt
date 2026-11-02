package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate

private val chainOrder = listOf(
	StoryCalendar.YEAR,
	StoryCalendar.MONTH,
	StoryCalendar.DAY,
	StoryCalendar.HOUR,
	StoryCalendar.MINUTE,
)

/**
 * A one-line form such as `year=97 day=210`: calendar units coarsest first, then any unknown units
 * alphabetically. [parseStoryDateText] reads it back.
 */
fun StoryDate.toCompactText(): String =
	rungs.entries
		.sortedWith(compareBy({ unitRank(it.key) }, { it.key }))
		.joinToString(" ") { (unit, value) -> "$unit=$value" }

private fun unitRank(unit: String): Int = chainOrder.indexOf(unit).takeIf { it >= 0 } ?: chainOrder.size

/** Whitespace-separated `unit=number` pairs. Null when [text] is blank or any pair is malformed. */
fun parseStoryDateText(text: String): StoryDate? {
	val pairs = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
	if (pairs.isEmpty()) return null

	val rungs = LinkedHashMap<String, Long>()
	for (pair in pairs) {
		val unit = pair.substringBefore('=', missingDelimiterValue = "")
		val value = pair.substringAfter('=', missingDelimiterValue = "").toLongOrNull()
		if (unit.isEmpty() || value == null || unit in rungs) return null
		rungs[unit] = value
	}
	return StoryDate(rungs)
}
