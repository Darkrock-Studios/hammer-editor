package com.darkrockstudios.apps.hammer.base.timeline

/**
 * Proleptic Gregorian arithmetic with astronomical year numbering (year 0 is 1 BCE, year -1 is
 * 2 BCE). Day counts are relative to 1970-01-01. After Howard Hinnant's civil-days algorithms.
 */
internal object GregorianArithmetic {
	fun isLeapYear(year: Long): Boolean = year % 4 == 0L && (year % 100 != 0L || year % 400 == 0L)

	fun daysInMonth(year: Long, month: Int): Int = when (month) {
		2 -> if (isLeapYear(year)) 29 else 28
		4, 6, 9, 11 -> 30
		else -> 31
	}

	fun daysFromCivil(year: Long, month: Int, day: Int): Long {
		// The computational year starts in March so the leap day falls at its end.
		val y = if (month <= 2) year - 1 else year
		val era = (if (y >= 0) y else y - 399) / 400
		val yearOfEra = y - era * 400
		val monthFromMarch = (month + 9) % 12
		val dayOfYear = (153 * monthFromMarch + 2) / 5 + day - 1
		val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
		return era * 146097 + dayOfEra - 719468
	}
}
