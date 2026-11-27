package com.darkrockstudios.apps.hammer.common.components.actions

/** One command palette row: an action with its label and category already in the user's language. */
data class PaletteEntry(val id: ActionId, val label: String, val category: String, val chord: KeyChord?)

private const val SCORE_PREFIX = 400
private const val SCORE_WORD_START = 300
private const val SCORE_CONTAINS = 200
private const val SCORE_SUBSEQUENCE = 100
private const val SCORE_CATEGORY_PREFIX = 3
private const val SCORE_CATEGORY_WORD_START = 2
private const val SCORE_CATEGORY_CONTAINS = 1

/**
 * Narrows [entries] to those whose label or category matches [query], best first: a label prefix,
 * then a word prefix, then a substring, then the letters in order with the fewest skipped between
 * them. Below every label match, a category prefix, word prefix, then substring. Ties keep their
 * input order; a blank query keeps every entry as given.
 */
fun rankActions(query: String, entries: List<PaletteEntry>): List<PaletteEntry> {
	val needle = query.trim().lowercase()
	if (needle.isEmpty()) return entries
	return entries
		.mapNotNull { entry -> score(needle, entry)?.let { entry to it } }
		.sortedByDescending { it.second }
		.map { it.first }
}

private fun score(needle: String, entry: PaletteEntry): Int? =
	labelScore(needle, entry.label.lowercase()) ?: categoryScore(needle, entry.category.lowercase())

private fun labelScore(needle: String, label: String): Int? = when {
	label.startsWith(needle) -> SCORE_PREFIX
	hasWordStarting(label, needle) -> SCORE_WORD_START
	label.contains(needle) -> SCORE_CONTAINS
	else -> fewestGaps(needle, label)?.let { (SCORE_SUBSEQUENCE - it).coerceAtLeast(SCORE_CATEGORY_PREFIX + 1) }
}

private fun categoryScore(needle: String, category: String): Int? = when {
	category.startsWith(needle) -> SCORE_CATEGORY_PREFIX
	hasWordStarting(category, needle) -> SCORE_CATEGORY_WORD_START
	category.contains(needle) -> SCORE_CATEGORY_CONTAINS
	else -> null
}

private fun hasWordStarting(text: String, needle: String): Boolean =
	text.indices.any { it > 0 && !text[it - 1].isLetterOrDigit() && text.startsWith(needle, it) }

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
