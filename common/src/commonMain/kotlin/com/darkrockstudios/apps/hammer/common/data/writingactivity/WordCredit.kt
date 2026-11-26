package com.darkrockstudios.apps.hammer.common.data.writingactivity

import com.darkrockstudios.apps.hammer.base.markdown.countedProse
import com.darkrockstudios.apps.hammer.base.markdown.countedWordSpans
import io.github.petertrr.diffutils.algorithm.myers.MyersDiffWithLinearSpace

/**
 * A text's words as writing activity compares them: the words the word count counts
 * ([countedWordSpans] of the [countedProse]), lowercased, each knowing the line it sits on.
 */
class ActivityWords private constructor(
	val words: List<String>,
	private val hashes: IntArray,
	private val lineStart: IntArray,
	private val lineSize: IntArray,
) {
	val size: Int get() = words.size

	/**
	 * The hash of every [SHINGLE_SIZE]-word window, and of every line holding fewer words than
	 * that, so a short line moved on its own is still recognised.
	 */
	fun shingles(): Sequence<Long> = sequence {
		for (start in 0..size - SHINGLE_SIZE) yield(windowHash(start))
		var index = 0
		while (index < size) {
			if (lineSize[index] < SHINGLE_SIZE) yield(lineHash(index))
			index += lineSize[index]
		}
	}

	/** Whether the word at [index] lies in a window or short line whose shingle is in [reproduced]. */
	fun isReproduced(index: Int, reproduced: Set<Long>): Boolean {
		if (reproduced.isEmpty()) return false
		if (lineSize[index] < SHINGLE_SIZE && lineHash(lineStart[index]) in reproduced) return true
		val first = maxOf(0, index - SHINGLE_SIZE + 1)
		val last = minOf(index, size - SHINGLE_SIZE)
		return (first..last).any { windowHash(it) in reproduced }
	}

	private fun windowHash(start: Int): Long = hashRun(WINDOW_SEED, start, SHINGLE_SIZE)

	private fun lineHash(start: Int): Long = hashRun(LINE_SEED, start, lineSize[start])

	private fun hashRun(seed: Long, start: Int, count: Int): Long {
		var hash = seed
		for (i in start until start + count) hash = (hash xor hashes[i].toLong()) * FNV_PRIME
		return hash
	}

	companion object {
		const val SHINGLE_SIZE = 4

		private const val WINDOW_SEED = -0x340d631b7bdddcdbL
		private const val LINE_SEED = 0x1f3d5b79a2c4e6f8L
		private const val FNV_PRIME = 0x100000001b3L

		fun of(source: String): ActivityWords {
			val text = countedProse(source)
			val words = ArrayList<String>()
			val lines = ArrayList<Int>()
			var line = 0
			var scanned = 0
			for (span in countedWordSpans(text)) {
				for (i in scanned until span.first) if (text[i] == '\n') line++
				scanned = span.last + 1
				words += text.substring(span).lowercase()
				lines += line
			}
			val lineStart = IntArray(words.size)
			val lineSize = IntArray(words.size)
			var start = 0
			while (start < words.size) {
				var end = start + 1
				while (end < words.size && lines[end] == lines[start]) end++
				for (i in start until end) {
					lineStart[i] = start
					lineSize[i] = end - start
				}
				start = end
			}
			return ActivityWords(words, IntArray(words.size) { words[it].hashCode() }, lineStart, lineSize)
		}
	}
}

/**
 * The words of [current] typed since [baseline], less those inside a window or short line whose
 * shingle is in [reproduced] (text moved, pasted back or restored rather than written).
 *
 * The common prefix and suffix are trimmed, then a word diff finds the inserted words. When either
 * remaining span exceeds [MAX_DIFFED_WORDS], a bag difference of the spans stands in for the diff.
 */
fun countOriginalWords(baseline: ActivityWords, current: ActivityWords, reproduced: Set<Long>): Int {
	val old = baseline.words
	val new = current.words
	val shorter = minOf(old.size, new.size)
	var prefix = 0
	while (prefix < shorter && old[prefix] == new[prefix]) prefix++
	var suffix = 0
	while (suffix < shorter - prefix && old[old.size - 1 - suffix] == new[new.size - 1 - suffix]) suffix++
	val oldSpan = old.subList(prefix, old.size - suffix)
	val newSpan = new.subList(prefix, new.size - suffix)
	val isOriginal = { index: Int -> !current.isReproduced(index + prefix, reproduced) }
	return when {
		newSpan.isEmpty() -> 0
		oldSpan.isEmpty() -> newSpan.indices.count(isOriginal)
		maxOf(oldSpan.size, newSpan.size) > MAX_DIFFED_WORDS -> bagCredit(oldSpan, newSpan, isOriginal)
		else -> insertedRuns(oldSpan, newSpan).sumOf { it.count(isOriginal) }
	}
}

/**
 * Past this many words on either side of an edit the diff gives way to a bag difference. A worst
 * case at this size (nothing in common) diffs in about 60 ms on a desktop JVM; 1,000 words take 15.
 */
const val MAX_DIFFED_WORDS = 2_000

/**
 * In a rewritten stretch the diff matches stray common words ("the", "and") between the changes.
 * Changes separated by at most [MAX_ISLAND_WORDS] matched words form one stretch, and a stretch that
 * deletes and inserts at least [MIN_REWRITE_WORDS] words is taken as typed whole.
 */
private const val MAX_ISLAND_WORDS = 1
private const val MIN_REWRITE_WORDS = 6

/** One stretch of a diff between matched words: [deleted] baseline words gave way to [inserted]. */
private class Hunk(var deleted: Int, var inserted: IntRange)

/** The runs of [current] (indices into it) that a word diff against [baseline] finds inserted. */
private fun insertedRuns(baseline: List<String>, current: List<String>): List<IntRange> {
	val hunks = mutableListOf<Hunk>()
	var originalEnd = -1
	for (change in MyersDiffWithLinearSpace<String>().computeDiff(baseline, current)) {
		val last = hunks.lastOrNull()
		val hunk = if (last != null && change.startOriginal == originalEnd &&
			change.startRevised == last.inserted.last + 1
		) {
			last
		} else {
			Hunk(0, change.startRevised until change.startRevised).also { hunks += it }
		}
		hunk.deleted += change.endOriginal - change.startOriginal
		hunk.inserted = hunk.inserted.first until change.endRevised
		originalEnd = change.endOriginal
	}
	val runs = mutableListOf<IntRange>()
	var zone = mutableListOf<Hunk>()
	fun closeZone() {
		val rewrite = zone.sumOf { it.deleted } >= MIN_REWRITE_WORDS &&
			zone.sumOf { it.inserted.last - it.inserted.first + 1 } >= MIN_REWRITE_WORDS
		if (rewrite) {
			runs += zone.first().inserted.first..zone.last().inserted.last
		} else {
			zone.mapNotNullTo(runs) { hunk -> hunk.inserted.takeUnless { it.isEmpty() } }
		}
	}
	for (hunk in hunks) {
		if (zone.isNotEmpty() && hunk.inserted.first - (zone.last().inserted.last + 1) > MAX_ISLAND_WORDS) {
			closeZone()
			zone = mutableListOf()
		}
		zone += hunk
	}
	if (zone.isNotEmpty()) closeZone()
	return runs
}

/**
 * Per word, the occurrences [current] has beyond [baseline], credited only as far as that word has
 * occurrences [isOriginal] accepts, since a bag cannot say which occurrence is the new one.
 */
private fun bagCredit(baseline: List<String>, current: List<String>, isOriginal: (Int) -> Boolean): Int {
	val surplus = current.groupingBy { it }.eachCount().toMutableMap()
	for (word in baseline) surplus[word]?.let { surplus[word] = it - 1 }
	val original = mutableMapOf<String, Int>()
	current.forEachIndexed { index, word ->
		if ((surplus[word] ?: 0) > 0 && isOriginal(index)) original[word] = (original[word] ?: 0) + 1
	}
	return original.entries.sumOf { (word, count) -> minOf(count, surplus.getValue(word)) }
}
