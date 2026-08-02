package com.darkrockstudios.apps.hammer.common.data.tagindex

private val TAG_INPUT_SPLIT = Regex("""[\s,]+""")

private fun Char.isTagChar(): Boolean =
	isLetterOrDigit() ||
		category == CharCategory.NON_SPACING_MARK ||
		category == CharCategory.COMBINING_SPACING_MARK ||
		this == '_' ||
		this == '-'

/** A tag is a run of letters, digits and combining marks, plus `_` and `-`. */
private fun isValidTag(tag: String): Boolean = tag.isNotEmpty() && tag.all { it.isTagChar() }

fun cleanTags(tags: Set<String>): Set<String> =
	tags.asSequence()
		.map { it.trim().removePrefix("#") }
		.filter(::isValidTag)
		.toSet()

/**
 * Parse free-form user tag input into a normalized set: split on whitespace + commas,
 * strip leading `#`, drop pieces that aren't valid tags.
 */
fun parseTagInput(input: String): Set<String> =
	cleanTags(input.split(TAG_INPUT_SPLIT).toSet())
