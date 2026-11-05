package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.StoryDateProblem
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StrResStoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDate
import com.darkrockstudios.apps.hammer.timeline_date_bce
import com.darkrockstudios.apps.hammer.timeline_date_before
import com.darkrockstudios.apps.hammer.timeline_date_before_epoch
import com.darkrockstudios.apps.hammer.timeline_date_problem_not_prefix
import com.darkrockstudios.apps.hammer.timeline_date_problem_out_of_range
import com.darkrockstudios.apps.hammer.timeline_date_problem_unknown_unit
import com.darkrockstudios.apps.hammer.timeline_date_shows_as
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

private val YearRungWidth = 76.dp
private val RungWidth = 56.dp

/**
 * A story date as one narrow numeric field per calendar unit, coarsest first, each captioned with
 * its unit. Trailing units may stay empty: the filled ones are the date's precision. A STORY
 * calendar with an epoch adds a `BEFORE <EPOCH>` toggle that negates the coarsest rung; without an
 * epoch that rung takes a minus sign. A caption under the row shows the date as it will read, or
 * what is wrong with it.
 *
 *     START
 *     YEAR     DAY    HOUR   MINUTE   ┌────────────────┐
 *     12       40                     │ BEFORE LANDING │
 *     ──────   ────   ────   ────     └────────────────┘
 *     SHOWS AS  Year 12 · Day 40
 *
 * Emits null while the rungs are empty or do not form a valid date, so a caller never holds a stale
 * one; the rungs keep what was typed. A [value] the field did not emit replaces the rungs.
 * With a [testTag], each rung is tagged `<testTag>-<unit>` and the toggle `<testTag>-before`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdRungDateField(
	label: String,
	calendar: CalendarDef,
	value: StoryDate?,
	onValueChange: (StoryDate?) -> Unit,
	modifier: Modifier = Modifier,
	error: String? = null,
	onFocusChanged: (Boolean) -> Unit = {},
	testTag: String? = null,
) {
	val epoch = calendar.epochName?.trim()?.takeIf { it.isNotEmpty() && calendar.preset == CalendarPreset.STORY }

	var lastEmitted by remember(calendar) { mutableStateOf(value) }
	var units by remember(calendar) { mutableStateOf(rungUnits(calendar, value)) }
	val texts = remember(calendar) { mutableStateMapOf<String, String>().apply { putAll(rungTexts(units, value, epoch)) } }
	var before by remember(calendar) { mutableStateOf(isBefore(units, value, epoch)) }
	val focused = remember { mutableStateMapOf<String, Boolean>() }

	if (value != lastEmitted) {
		lastEmitted = value
		units = rungUnits(calendar, value)
		texts.clear()
		texts.putAll(rungTexts(units, value, epoch))
		before = isBefore(units, value, epoch)
	}

	val parsed = parseRungs(calendar, units, texts, before)

	fun emit() {
		val date = parseRungs(calendar, units, texts, before).date
		lastEmitted = date
		onValueChange(date)
	}

	val words = rememberStoryDateWords()
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		HdMonoLabel(text = label)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			units.forEachIndexed { index, unit ->
				val leading = index == 0
				HdHairlineField(
					label = storyUnitName(unit.name),
					value = texts[unit.name].orEmpty(),
					onValueChange = { typed ->
						texts[unit.name] = filterRung(typed, allowMinus = leading && epoch == null)
						emit()
					},
					keyboardType = KeyboardType.Number,
					capitalization = KeyboardCapitalization.None,
					autoCorrectEnabled = false,
					onFocusChanged = { hasFocus ->
						focused[unit.name] = hasFocus
						onFocusChanged(focused.values.any { it })
					},
					testTag = testTag?.let { "$it-${unit.name}" },
					modifier = Modifier.width(if (unit.name == StoryCalendar.YEAR) YearRungWidth else RungWidth),
				)
			}
			if (epoch != null) {
				BeforeEpochCell(
					label = Res.string.timeline_date_before.get(epoch),
					active = before,
					onToggle = {
						before = !before
						emit()
					},
					modifier = Modifier
						.padding(top = 16.dp)
						.then(if (testTag != null) Modifier.testTag("$testTag-before") else Modifier),
				)
			}
		}

		val problem = parsed.problem
		val date = parsed.date
		when {
			problem != null -> HdMonoLabel(text = problem.message(), color = MaterialTheme.colorScheme.error)
			error != null -> HdMonoLabel(text = error, color = MaterialTheme.colorScheme.error)
			date != null -> Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				HdMonoLabel(text = Res.string.timeline_date_shows_as.get())
				Text(
					text = formatStoryDate(calendar, date, words),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = if (testTag != null) Modifier.testTag("$testTag-shows") else Modifier,
				)
			}
		}
	}
}

/** A hairline cell that reads as pressed while [active], in the segmented picker's vocabulary. */
@Composable
private fun BeforeEpochCell(
	label: String,
	active: Boolean,
	onToggle: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val border = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
	val ink = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 32.dp)
			.border(width = Dp.Hairline, color = border, shape = RectangleShape)
			.clickable(onClick = onToggle)
			.semantics {
				role = Role.Switch
				selected = active
			}
			.padding(horizontal = 10.dp, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = label, color = ink)
	}
}

private class ParsedRungs(val date: StoryDate?, val problem: StoryDateProblem?)

/** [entryUnits], widened up to the coarsest unit [value] already has. */
private fun rungUnits(calendar: CalendarDef, value: StoryDate?): List<CalendarUnit> {
	val chain = StoryCalendar.units(calendar.preset)
	val entry = entryUnits(calendar)
	val valueTop = value?.rungs?.keys?.mapNotNull { key -> chain.indexOfFirst { it.name == key }.takeIf { it >= 0 } }?.minOrNull()
	val entryTop = chain.indexOf(entry.first())
	return if (valueTop != null && valueTop < entryTop) chain.drop(valueTop) else entry
}

private fun rungTexts(units: List<CalendarUnit>, value: StoryDate?, epoch: String?): Map<String, String> =
	units.withIndex().associate { (index, unit) ->
		val number = value?.get(unit.name)
		val shown = if (number != null && index == 0 && epoch != null && number < 0) -number else number
		unit.name to (shown?.toString() ?: "")
	}

private fun isBefore(units: List<CalendarUnit>, value: StoryDate?, epoch: String?): Boolean =
	epoch != null && (value?.get(units.first().name) ?: 0L) < 0L

private fun filterRung(typed: String, allowMinus: Boolean): String {
	val digits = typed.filter { it.isDigit() }.take(MAX_RUNG_DIGITS)
	return if (allowMinus && typed.startsWith("-")) "-$digits" else digits
}

private const val MAX_RUNG_DIGITS = 10

private fun parseRungs(
	calendar: CalendarDef,
	units: List<CalendarUnit>,
	texts: Map<String, String>,
	before: Boolean,
): ParsedRungs {
	val rungs = LinkedHashMap<String, Long>()
	units.forEachIndexed { index, unit ->
		val text = texts[unit.name].orEmpty()
		if (text.isEmpty()) return@forEachIndexed
		// A lone minus sign is a number still being typed.
		val number = text.toLongOrNull() ?: return ParsedRungs(null, null)
		rungs[unit.name] = if (index == 0 && before) -number else number
	}
	if (rungs.isEmpty()) return ParsedRungs(null, null)
	val date = StoryDate(rungs)
	val problem = StoryCalendar.validate(calendar, date)
	return if (problem == null) ParsedRungs(date, null) else ParsedRungs(null, problem)
}

/** A calendar unit's name, translated where the app knows it. */
@Composable
fun storyUnitName(unit: String): String = when (unit) {
	StoryCalendar.YEAR -> Res.string.timeline_unit_year.get()
	StoryCalendar.MONTH -> Res.string.timeline_unit_month.get()
	StoryCalendar.DAY -> Res.string.timeline_unit_day.get()
	StoryCalendar.HOUR -> Res.string.timeline_unit_hour.get()
	StoryCalendar.MINUTE -> Res.string.timeline_unit_minute.get()
	else -> unit
}

@Composable
private fun StoryDateProblem.message(): String = when (this) {
	is StoryDateProblem.OutOfRange ->
		Res.string.timeline_date_problem_out_of_range.get(storyUnitName(unit), value)

	is StoryDateProblem.UnknownUnit -> Res.string.timeline_date_problem_unknown_unit.get(unit)
	is StoryDateProblem.UnknownCoarsestUnit -> Res.string.timeline_date_problem_unknown_unit.get(unit)
	StoryDateProblem.CoarsestUnitNotSupported,
	StoryDateProblem.NotAPrefix -> Res.string.timeline_date_problem_not_prefix.get()
}

/** [StoryDateWords] from the app's strings, for [formatStoryDate]. */
@Composable
fun rememberStoryDateWords(): StoryDateWords {
	val units = mapOf(
		StoryCalendar.YEAR to Res.string.timeline_unit_year.get(),
		StoryCalendar.MONTH to Res.string.timeline_unit_month.get(),
		StoryCalendar.DAY to Res.string.timeline_unit_day.get(),
		StoryCalendar.HOUR to Res.string.timeline_unit_hour.get(),
		StoryCalendar.MINUTE to Res.string.timeline_unit_minute.get(),
	)
	val months = listOf(
		Res.string.timeline_month_1.get(), Res.string.timeline_month_2.get(), Res.string.timeline_month_3.get(),
		Res.string.timeline_month_4.get(), Res.string.timeline_month_5.get(), Res.string.timeline_month_6.get(),
		Res.string.timeline_month_7.get(), Res.string.timeline_month_8.get(), Res.string.timeline_month_9.get(),
		Res.string.timeline_month_10.get(), Res.string.timeline_month_11.get(), Res.string.timeline_month_12.get(),
	)
	val beforeTemplate = Res.string.timeline_date_before_epoch.get()
	val bceTemplate = Res.string.timeline_date_bce.get()
	return remember(units, months, beforeTemplate, bceTemplate) {
		StrResStoryDateWords(units, months, beforeTemplate, bceTemplate)
	}
}
