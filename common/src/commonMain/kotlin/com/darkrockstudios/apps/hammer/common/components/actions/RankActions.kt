package com.darkrockstudios.apps.hammer.common.components.actions

/** One command palette row: an action with its label and category already in the user's language. */
data class PaletteEntry(val id: ActionId, val label: String, val category: String, val chord: KeyChord?)

private const val SCORE_PREFIX = 400
private const val SCORE_WORD_START = 300
private const val SCORE_CONTAINS = 200
private const val SCORE_SUBSEQUENCE = 100

/**
 * Narrows [entries] to those whose label matches [query], best first: a label prefix, then a word
 * prefix, then a substring, then the letters in order with the fewest skipped between them. Ties
 * keep their input order; a blank query keeps every entry as given.
 */
fun rankActions(query: String, entries: List<PaletteEntry>): List<PaletteEntry> {
	val needle = query.trim().lowercase()
	if (needle.isEmpty()) return entries
	return entries
		.mapNotNull { entry -> score(needle, entry.label.lowercase())?.let { entry to it } }
		.sortedByDescending { it.second }
		.map { it.first }
}

private fun score(needle: String, label: String): Int? = when {
	label.startsWith(needle) -> SCORE_PREFIX
	label.indices.any { it > 0 && !label[it - 1].isLetterOrDigit() && label.startsWith(needle, it) } -> SCORE_WORD_START
	label.contains(needle) -> SCORE_CONTAINS
	else -> fewestGaps(needle, label)?.let { (SCORE_SUBSEQUENCE - it).coerceAtLeast(1) }
}

/** Skipped characters between the first and last matched letter, over every start of the match. */
private fun fewestGaps(needle: String, label: String): Int? {
	var best: Int? = null
	for (start in label.indices) {
		if (label[start] != needle[0]) continue
		var at = start
		var matched = 1
		while (matched < needle.length) {
			at = label.indexOf(needle[matched], at + 1)
			if (at < 0) break
			matched++
		}
		if (matched < needle.length) break
		val gaps = at - start + 1 - needle.length
		if (best == null || gaps < best) best = gaps
	}
	return best
}
