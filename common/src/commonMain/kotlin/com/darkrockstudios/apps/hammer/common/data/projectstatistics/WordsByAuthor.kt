package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.writingactivity.AttributedSlot
import kotlin.time.Instant

/**
 * Lifetime words per author, most first, with each author's devices nested. Empty unless
 * more than one author has written, so a single-author project keeps its by-device view.
 * An author's name is the one on their most recently active slot.
 */
fun wordsByAuthor(slots: List<AttributedSlot>): List<AuthorWords> {
	val authors = slots.groupBy { it.authorId }.mapNotNull { (authorId, authorSlots) ->
		val devices = mutableMapOf<String, Int>()
		authorSlots.forEach { slot ->
			val words = slot.log.sessions.sumOf { it.wordsWritten.coerceAtLeast(0) }
			if (words > 0) devices[slot.log.deviceLabel] = (devices[slot.log.deviceLabel] ?: 0) + words
		}
		if (devices.isEmpty()) return@mapNotNull null
		val name = authorSlots
			.filter { it.authorName != null }
			.maxByOrNull { slot -> slot.log.sessions.maxOfOrNull { it.endedAt } ?: Instant.DISTANT_PAST }
			?.authorName
		AuthorWords(authorId, name, devices.values.sum(), devices)
	}
	return if (authors.size > 1) authors.sortedByDescending { it.words } else emptyList()
}
