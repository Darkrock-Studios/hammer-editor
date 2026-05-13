package com.darkrockstudios.apps.hammer.common.data.tagindex

private val TAG_PATTERN = Regex("""[\w-]+""")
private val TAG_INPUT_SPLIT = Regex("""[\s,]+""")

fun cleanTags(tags: Set<String>): Set<String> =
	tags.asSequence()
		.map { it.trim().removePrefix("#") }
		.filter { it.isNotEmpty() && TAG_PATTERN.matches(it) }
		.toSet()

/**
 * Parse free-form user tag input into a normalized set: split on whitespace + commas,
 * strip leading `#`, drop pieces that don't match [TAG_PATTERN].
 */
fun parseTagInput(input: String): Set<String> =
	cleanTags(input.split(TAG_INPUT_SPLIT).toSet())
