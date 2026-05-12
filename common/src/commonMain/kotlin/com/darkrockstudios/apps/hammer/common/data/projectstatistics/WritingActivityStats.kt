package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class BestDay(val date: LocalDate, val words: Int)

@Serializable
data class WritingActivityDerived(
	val wordsToday: Int,
	val wordsThisWeek: Int,
	val wordsLastWeek: Int,
	val weekChangePercent: Int?,
	val dailyAverageThisWeek: Int,
	val currentStreak: Int,
	val longestStreak: Int,
	val daysWritten: Int,
	val bestDayInStreak: BestDay?,
	val avgWeekday: Int,
	val avgWeekend: Int,
) {
	companion object {
		val Empty = WritingActivityDerived(
			wordsToday = 0,
			wordsThisWeek = 0,
			wordsLastWeek = 0,
			weekChangePercent = null,
			dailyAverageThisWeek = 0,
			currentStreak = 0,
			longestStreak = 0,
			daysWritten = 0,
			bestDayInStreak = null,
			avgWeekday = 0,
			avgWeekend = 0,
		)
	}
}

/**
 * Parse the raw `dailyWordTotals` map (string-keyed for TOML serialization)
 * into a [LocalDate]-keyed map. Unparseable keys are silently dropped.
 */
fun parseDailyWordTotals(dailyWordTotals: Map<String, Int>): Map<LocalDate, Int> =
	dailyWordTotals.mapNotNull { (key, value) ->
		runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to value }
	}.toMap()

/**
 * Derives time-sensitive writing-activity stats from a per-day word total map.
 *
 * The map only needs entries for days the user actually wrote (positive values);
 * missing dates are treated as zero. All results are computed relative to [today].
 */
fun deriveWritingStats(
	dailyTotals: Map<LocalDate, Int>,
	today: LocalDate,
): WritingActivityDerived {
	if (dailyTotals.isEmpty()) return WritingActivityDerived.Empty

	val written = dailyTotals.filterValues { it > 0 }

	val wordsToday = written[today] ?: 0
	val wordsThisWeek = sumWindow(written, today.minus(6, DateTimeUnit.DAY), today)
	val wordsLastWeek = sumWindow(
		written,
		today.minus(13, DateTimeUnit.DAY),
		today.minus(7, DateTimeUnit.DAY),
	)
	val weekChangePercent = if (wordsLastWeek == 0) {
		null
	} else {
		(((wordsThisWeek - wordsLastWeek).toDouble() / wordsLastWeek) * 100.0).roundToInt()
	}
	val dailyAverageThisWeek = wordsThisWeek / 7

	val currentStreak = computeCurrentStreak(written, today)
	val longestStreak = computeLongestStreak(written)
	val daysWritten = written.size

	val bestDayInStreak = if (currentStreak <= 1) {
		null
	} else {
		val streakStart = streakAnchor(written, today).minus(currentStreak - 1, DateTimeUnit.DAY)
		val streakEnd = streakAnchor(written, today)
		bestDayBetween(written, streakStart, streakEnd)
	}

	val avgWeekday = average(written.filter { it.key.dayOfWeek.isWeekday() }.values)
	val avgWeekend = average(written.filter { !it.key.dayOfWeek.isWeekday() }.values)

	return WritingActivityDerived(
		wordsToday = wordsToday,
		wordsThisWeek = wordsThisWeek,
		wordsLastWeek = wordsLastWeek,
		weekChangePercent = weekChangePercent,
		dailyAverageThisWeek = dailyAverageThisWeek,
		currentStreak = currentStreak,
		longestStreak = longestStreak,
		daysWritten = daysWritten,
		bestDayInStreak = bestDayInStreak,
		avgWeekday = avgWeekday,
		avgWeekend = avgWeekend,
	)
}

private fun DayOfWeek.isWeekday(): Boolean =
	this != DayOfWeek.SATURDAY && this != DayOfWeek.SUNDAY

private fun sumWindow(written: Map<LocalDate, Int>, from: LocalDate, to: LocalDate): Int {
	var sum = 0
	var d = from
	while (d <= to) {
		sum += written[d] ?: 0
		d = d.plusDays(1)
	}
	return sum
}

private fun LocalDate.plusDays(days: Int): LocalDate =
	this.minus(-days, DateTimeUnit.DAY)

/**
 * Finds the anchor day for the current streak: today if written, else yesterday
 * if written, else null (no current streak).
 */
private fun streakAnchorOrNull(written: Map<LocalDate, Int>, today: LocalDate): LocalDate? {
	if (written.containsKey(today)) return today
	val yesterday = today.minus(1, DateTimeUnit.DAY)
	if (written.containsKey(yesterday)) return yesterday
	return null
}

private fun streakAnchor(written: Map<LocalDate, Int>, today: LocalDate): LocalDate =
	streakAnchorOrNull(written, today) ?: today

private fun computeCurrentStreak(written: Map<LocalDate, Int>, today: LocalDate): Int {
	val anchor = streakAnchorOrNull(written, today) ?: return 0
	var count = 0
	var d = anchor
	while (written.containsKey(d)) {
		count++
		d = d.minus(1, DateTimeUnit.DAY)
	}
	return count
}

private fun computeLongestStreak(written: Map<LocalDate, Int>): Int {
	if (written.isEmpty()) return 0
	val sorted = written.keys.sorted()
	var longest = 1
	var current = 1
	for (i in 1 until sorted.size) {
		if (sorted[i - 1].daysUntil(sorted[i]) == 1) {
			current++
			if (current > longest) longest = current
		} else {
			current = 1
		}
	}
	return longest
}

private fun bestDayBetween(
	written: Map<LocalDate, Int>,
	from: LocalDate,
	to: LocalDate,
): BestDay? {
	var best: BestDay? = null
	var d = from
	while (d <= to) {
		val w = written[d]
		if (w != null && (best == null || w > best.words)) {
			best = BestDay(d, w)
		}
		d = d.plusDays(1)
	}
	return best
}

private fun average(values: Collection<Int>): Int {
	if (values.isEmpty()) return 0
	return values.sum() / values.size
}
