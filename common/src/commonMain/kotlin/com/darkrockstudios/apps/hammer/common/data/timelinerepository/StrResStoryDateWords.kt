package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.timeline_date_bce
import com.darkrockstudios.apps.hammer.timeline_date_before_epoch
import com.darkrockstudios.apps.hammer.timeline_month_1
import com.darkrockstudios.apps.hammer.timeline_month_10
import com.darkrockstudios.apps.hammer.timeline_month_11
import com.darkrockstudios.apps.hammer.timeline_month_12
import com.darkrockstudios.apps.hammer.timeline_month_2
import com.darkrockstudios.apps.hammer.timeline_month_3
import com.darkrockstudios.apps.hammer.timeline_month_4
import com.darkrockstudios.apps.hammer.timeline_month_5
import com.darkrockstudios.apps.hammer.timeline_month_6
import com.darkrockstudios.apps.hammer.timeline_month_7
import com.darkrockstudios.apps.hammer.timeline_month_8
import com.darkrockstudios.apps.hammer.timeline_month_9
import com.darkrockstudios.apps.hammer.timeline_unit_day
import com.darkrockstudios.apps.hammer.timeline_unit_hour
import com.darkrockstudios.apps.hammer.timeline_unit_minute
import com.darkrockstudios.apps.hammer.timeline_unit_month
import com.darkrockstudios.apps.hammer.timeline_unit_year

/**
 * [StoryDateWords] in the app's language, for code outside composition. The strings are read once
 * when it is made, since [StrRes] is suspending and the words are not.
 */
class StrResStoryDateWords private constructor(
	private val units: Map<String, String>,
	private val months: List<String>,
	private val beforeTemplate: String,
	private val bceTemplate: String,
) : StoryDateWords {

	override fun unit(unit: String): String = units[unit] ?: StoryDateWords.English.unit(unit)
	override fun month(month: Int): String = months.getOrElse(month - 1) { month.toString() }
	override fun beforeEpoch(text: String, epoch: String): String =
		beforeTemplate.replace("%1\$s", text).replace("%2\$s", epoch)

	override fun bce(year: Long): String = bceTemplate.replace("%1\$d", year.toString())

	companion object {
		suspend operator fun invoke(strRes: StrRes): StrResStoryDateWords = StrResStoryDateWords(
			units = mapOf(
				StoryCalendar.YEAR to strRes.get(Res.string.timeline_unit_year),
				StoryCalendar.MONTH to strRes.get(Res.string.timeline_unit_month),
				StoryCalendar.DAY to strRes.get(Res.string.timeline_unit_day),
				StoryCalendar.HOUR to strRes.get(Res.string.timeline_unit_hour),
				StoryCalendar.MINUTE to strRes.get(Res.string.timeline_unit_minute),
			),
			months = listOf(
				Res.string.timeline_month_1, Res.string.timeline_month_2, Res.string.timeline_month_3,
				Res.string.timeline_month_4, Res.string.timeline_month_5, Res.string.timeline_month_6,
				Res.string.timeline_month_7, Res.string.timeline_month_8, Res.string.timeline_month_9,
				Res.string.timeline_month_10, Res.string.timeline_month_11, Res.string.timeline_month_12,
			).map { strRes.get(it) },
			// Read as templates and filled per call, as the Compose words do.
			beforeTemplate = strRes.get(Res.string.timeline_date_before_epoch),
			bceTemplate = strRes.get(Res.string.timeline_date_bce),
		)
	}
}
