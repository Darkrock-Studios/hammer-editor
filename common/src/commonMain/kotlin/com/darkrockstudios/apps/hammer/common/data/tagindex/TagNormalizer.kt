package com.darkrockstudios.apps.hammer.common.data.tagindex

private val TAG_PATTERN = Regex("""[\w-]+""")

fun cleanTags(tags: Set<String>): Set<String> =
	tags.asSequence()
		.map { it.trim().removePrefix("#") }
		.filter { it.isNotEmpty() && TAG_PATTERN.matches(it) }
		.toSet()
