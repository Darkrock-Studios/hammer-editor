package com.darkrockstudios.apps.hammer.common.data.writingactivity

/**
 * Counts how many words appear in [newText] that aren't already accounted
 * for in the bag of words from [oldText]. Multiset-based: tokenises both
 * texts on whitespace and pairs each new word against an unused occurrence
 * of the same word in the old bag; anything left unpaired counts as added.
 *
 * Position is ignored, so a pure rearrangement returns 0 — the writer
 * didn't produce any *new* vocabulary, they just reorganised. That's the
 * intent: "how many new words did the writer actually type?", not "how
 * much did the text change?"
 *
 * Performance: O(m + n) using a hash map; trivially fast even for very
 * long scenes. The pre-edit baseline (held in memory by the tracker) is
 * what makes this work — without it we couldn't distinguish "wrote 100
 * new words" from "rewrote a 100-word paragraph". Holding the actual text
 * also keeps the door open for swapping in a richer diff later if we
 * decide moves should count.
 */
fun countAddedWords(oldText: String, newText: String): Int {
	val remainingOld = tokenizeWords(oldText)
		.groupingBy { it }
		.eachCount()
		.toMutableMap()

	var added = 0
	for (word in tokenizeWords(newText)) {
		val remaining = remainingOld[word] ?: 0
		if (remaining > 0) {
			remainingOld[word] = remaining - 1
		} else {
			added++
		}
	}
	return added
}

private fun tokenizeWords(text: String): Sequence<String> =
	WORD_TOKEN.findAll(text).map { it.value }

private val WORD_TOKEN = Regex("""\S+""")
