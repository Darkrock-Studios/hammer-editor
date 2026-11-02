package com.darkrockstudios.apps.hammer.common.timeline.layout.samples

import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutEvent
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.TickScheme
import com.darkrockstudios.apps.hammer.common.timeline.layout.TickUnit
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The case studies from the Timeline 2.0 design, as layout inputs for previews and tests.
 * Positions are minutes on a story calendar (365-day years).
 */
object TimelineLayoutSamples {
	const val MINUTE = 1L
	const val HOUR = 60L
	const val DAY = 24 * HOUR
	const val YEAR = 365 * DAY

	private val minuteUnit = TickUnit("minute", MINUTE)
	private val hourUnit = TickUnit("hour", HOUR)
	private val dayUnit = TickUnit("day", DAY)
	private val yearUnit = TickUnit("year", YEAR)

	private fun pad(n: Long): String = n.toString().padStart(2, '0')

	private fun clock(position: Long): String {
		val minuteOfDay = position.mod(DAY)
		return pad(minuteOfDay / HOUR) + ":" + pad(minuteOfDay % HOUR)
	}

	/** "Day 3", or "Day 3 · 14:00" below a day; day 1 starts at position 0. */
	val dayClockTicks = TickScheme(listOf(dayUnit, hourUnit, minuteUnit)) { position, unit, short ->
		val day = position.floorDiv(DAY) + 1
		when {
			unit == dayUnit -> if (short) "D$day" else "Day $day"
			short -> clock(position)
			else -> "Day $day · ${clock(position)}"
		}
	}

	/** Days either side of an event at position 0: "Day -12", "Day +5". */
	val signedDayTicks = TickScheme(listOf(dayUnit, hourUnit)) { position, unit, short ->
		val day = position.floorDiv(DAY)
		val signed = if (day < 0) "$day" else "+$day"
		when {
			short -> signed
			unit == dayUnit -> "Day $signed"
			else -> "Day $signed · ${clock(position)}"
		}
	}

	/** Years from Landing: "1800 before Landing", "Year 140 · Day 36". */
	val landingYearTicks = TickScheme(listOf(yearUnit, dayUnit, hourUnit)) { position, unit, short ->
		val year = position.floorDiv(YEAR)
		val dayOfYear = position.mod(YEAR) / DAY
		val yearText = when {
			short && year < 0 -> "−${abs(year)}"
			short -> "Y$year"
			year < 0 -> "${abs(year)} before Landing"
			else -> "Year $year"
		}
		when {
			unit == yearUnit || dayOfYear == 0L -> yearText
			short -> "D$dayOfYear"
			else -> "$yearText · Day $dayOfYear"
		}
	}

	private class Builder(private val scale: Double) {
		val events = ArrayList<LayoutEvent>()

		fun at(value: Double): Long = (value * scale).roundToLong()

		fun add(
			id: Int,
			title: String,
			start: Double,
			end: Double? = null,
			kind: LayoutKind = LayoutKind.EVENT,
			lane: String? = null,
			sub: String? = null,
			milestone: Boolean = false,
		) {
			events += LayoutEvent(
				id = id,
				kind = kind,
				title = title,
				subtitle = sub,
				lane = lane,
				milestone = milestone,
				order = events.size,
				start = at(start),
				end = end?.let { at(it) },
			)
		}
	}

	private fun input(
		events: List<LayoutEvent>,
		lanes: List<String>,
		ticks: TickScheme,
		phone: Boolean,
		frame: TimelineFrame = TimelineFrame.Everything,
		view: TimelineView = TimelineView.TO_SCALE,
		selectedId: Int? = null,
	) = TimelineLayoutInput(
		events = events,
		laneOrder = lanes,
		view = view,
		frame = frame,
		widthDp = if (phone) 360f else 840f,
		phone = phone,
		ticks = ticks,
		selectedId = selectedId,
	)

	/** No lanes: a milestone, two unlaned spans, two scenes and a point, all in the ledger. */
	fun minimal(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val b = Builder(HOUR.toDouble())
		b.add(1, "Storm closes the pass", 24.0, milestone = true)
		b.add(2, "The Ferris wedding", 24.0 * 2 + 14, 24.0 * 2 + 22)
		b.add(3, "Harvest festival", 24.0 * 4, 24.0 * 6, kind = LayoutKind.ENTRY, sub = "encyclopedia event")
		b.add(4, "Mara leaves town", 24.0 * 3 + 6, kind = LayoutKind.SCENE, sub = "Ch 1")
		b.add(5, "The letter arrives", 24.0 * 5 + 9, kind = LayoutKind.SCENE, sub = "Ch 2")
		b.add(6, "Bridge reopens", 24.0 * 7 + 12)
		return input(b.events, emptyList(), dayClockTicks, phone, selectedId = selectedId)
	}

	const val HOLLOWMERE_LORD = "Lord Hollowmere"
	const val HOLLOWMERE_HAYES = "Col. Hayes"
	const val HOLLOWMERE_WARD = "Mrs. Ward"
	const val HOLLOWMERE_PIKE = "Pike"

	/** One evening in a country house: four whereabouts lanes, scenes and sounds in the ledger. */
	fun hollowmere(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val v = HOLLOWMERE_LORD
		val h = HOLLOWMERE_HAYES
		val w = HOLLOWMERE_WARD
		val p = HOLLOWMERE_PIKE
		val scene = LayoutKind.SCENE
		val b = Builder(HOUR.toDouble())
		b.add(1, "Dinner", 19.0, 20.5, scene, sub = "Ch 2")
		b.add(2, "Billiards", 20.5, 22.0, scene, sub = "Ch 3 · Hayes tells it")
		b.add(3, "Ward retires", 20.75, 21.25, scene, sub = "Ch 4")
		b.add(4, "The wine key", 21.0, 21.75, scene, sub = "Ch 5 · Pike tells it")
		b.add(5, "Lights out", 22.75, 23.25, scene, sub = "Ch 6")
		b.add(6, "The body", 23.5, 24.5, scene, sub = "Ch 1 · the opening")
		b.add(7, "Rook arrives", 25.0, 26.5, scene, sub = "Ch 7")
		b.add(8, "Dinner", 19.0, 20.5, lane = v)
		b.add(9, "Study, alone", 20.5, 21.5, lane = v)
		b.add(10, "Dead", 21.5, 26.5, lane = v)
		b.add(11, "Dinner", 19.0, 20.5, lane = h)
		b.add(12, "\"With Pike\"", 20.5, 21.0, lane = h)
		b.add(13, "No witness", 21.0, 21.75, lane = h)
		b.add(14, "Billiards", 21.75, 22.5, lane = h)
		b.add(15, "Drawing room", 22.5, 26.5, lane = h)
		b.add(16, "Dinner", 19.0, 20.5, lane = w)
		b.add(17, "Upstairs", 20.75, 21.7, lane = w)
		b.add(18, "Passes billiards", 21.75, lane = w)
		b.add(19, "Upstairs", 21.8, 23.4, lane = w)
		b.add(20, "Drawing room", 23.5, 26.5, lane = w)
		b.add(21, "Serving", 19.0, 20.5, lane = p)
		b.add(22, "Billiard room", 20.5, 21.0, lane = p)
		b.add(23, "Wine cellar", 21.0, 21.75, lane = p)
		b.add(24, "Pantry", 21.75, 23.4, lane = p)
		b.add(25, "Finds the body", 23.45, lane = p, milestone = true)
		b.add(26, "Drawing room", 23.5, 26.5, lane = p)
		b.add(27, "Dead by now (Ashby)", 21.5, milestone = true)
		b.add(28, "Power fails", 22.75)
		b.add(29, "\"Gunshot\"", 23.2)
		return input(b.events, listOf(v, h, w, p), dayClockTicks, phone, selectedId = selectedId)
	}

	/** Leviathan Wakes: eight years of prehistory folded away above sixty dense days. */
	fun leviathan(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val j = "Julie & the Scopuli"
		val h = "Holden & crew"
		val m = "Miller"
		val p = "Protomolecule"
		val b = Builder(DAY.toDouble())
		b.add(1, "Found on Phoebe", -2920.0, kind = LayoutKind.ENTRY, lane = p, sub = "Ch 40")
		b.add(2, "Scopuli taken", -12.0, lane = j, sub = "Ch 34")
		b.add(3, "Locker", -12.0, -4.0, lane = j)
		b.add(4, "Julie breaks out", -4.0, -3.0, LayoutKind.SCENE, sub = "Prologue")
		b.add(5, "Anubis to Eros", -3.0, 6.0, lane = j)
		b.add(6, "Blue Falcon", 6.0, 9.0, lane = j)
		b.add(7, "Dead, unfound", 9.0, 25.0, lane = j)
		b.add(8, "Canterbury answers the Scopuli", -1.0, 0.0, LayoutKind.SCENE, sub = "Ch 1")
		b.add(9, "Canterbury destroyed", 0.0, lane = h, milestone = true)
		b.add(10, "The Knight", 0.0, 2.0, lane = h)
		b.add(11, "Donnager", 2.0, 5.0, lane = h)
		b.add(12, "Donnager destroyed", 5.0, lane = h, milestone = true)
		b.add(13, "Rocinante", 5.0, 9.0, lane = h)
		b.add(14, "Tycho", 9.0, 22.0, lane = h)
		b.add(15, "Eros", 22.0, 27.0, lane = h)
		b.add(16, "Thoth raid", 35.0, 36.0, lane = h)
		b.add(17, "Chasing Eros", 40.0, 60.0, lane = h)
		b.add(18, "Miller gets the Mao job", -2.0, kind = LayoutKind.SCENE, sub = "Ch 2")
		b.add(19, "Ceres, the case", -2.0, 18.0, lane = m)
		b.add(20, "Fired", 18.0, lane = m)
		b.add(21, "To Eros", 19.0, 24.0, lane = m)
		b.add(22, "Miller finds Julie", 24.0, 25.0, LayoutKind.SCENE, sub = "Ch 24")
		b.add(23, "Eros", 24.0, 27.0, lane = m)
		b.add(24, "Irradiated", 27.0, 35.0, lane = m)
		b.add(25, "Thoth", 35.0, 36.0, lane = m)
		b.add(26, "Rides Eros", 45.0, 60.0, lane = m)
		b.add(27, "On the Anubis", -12.0, 6.0, lane = p)
		b.add(28, "In Julie, on Eros", 6.0, 25.0, lane = p)
		b.add(29, "Eros experiment begins", 25.0, lane = p, milestone = true)
		b.add(30, "Consuming Eros", 25.0, 40.0, lane = p)
		b.add(31, "Eros moves", 40.0, lane = p, milestone = true)
		b.add(32, "Eros hits Venus", 60.0, milestone = true)
		return input(b.events, listOf(j, h, m, p), signedDayTicks, phone, selectedId = selectedId)
	}

	private val insurgencyLanes = listOf("The Plan", "Politics", "Technology")

	/** Insurgency, Everything frame: millennia of transit and terraforming, then generations. */
	fun insurgencyEverything(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val (plan, politics, tech) = insurgencyLanes
		val b = Builder(YEAR.toDouble())
		b.add(1, "Transit", -1800.0, -800.0, lane = plan)
		b.add(2, "Terraforming", -800.0, 0.0, lane = plan)
		b.add(3, "Gen 0", 0.0, 30.0, lane = plan)
		b.add(4, "Gen 1", 30.0, 60.0, lane = plan)
		b.add(5, "Gen 2", 60.0, 90.0, lane = plan)
		b.add(6, "Gen 3", 90.0, 120.0, lane = plan)
		b.add(7, "Gen 4", 120.0, 150.0, lane = plan)
		b.add(8, "Launch", -1800.0, lane = plan)
		b.add(9, "Arrival", -800.0, lane = plan)
		b.add(10, "Landing", 0.0, lane = plan, milestone = true)
		b.add(11, "Famine", 127.0, 129.0, lane = politics)
		b.add(12, "Miners' Rebellion", 130.0, 131.0, LayoutKind.ENTRY, politics, sub = "encyclopedia event")
		b.add(13, "Kent's laws", 132.0, 139.0, lane = politics)
		b.add(14, "Adriel elected", 144.0, lane = politics, milestone = true)
		b.add(15, "Knowledge Base", 45.0, lane = tech)
		b.add(16, "Electronics", 100.0, lane = tech)
		b.add(17, "Automobiles", 122.0, lane = tech)
		b.add(18, "Firearms", 140.0, lane = tech)
		b.add(19, "Ch 1 · Ship Arrival", -800.0, 0.0, LayoutKind.SCENE, sub = "the only scene up here")
		b.add(20, "Chapters 2–4", 140.0, 143.0, sub = "the book")
		return input(b.events, insurgencyLanes, landingYearTicks, phone, selectedId = selectedId)
	}

	/** Insurgency, Story frame: the years the book's scenes cover. */
	fun insurgencyStory(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val (plan, politics, tech) = insurgencyLanes
		val scene = LayoutKind.SCENE
		val b = Builder(YEAR.toDouble())
		b.add(1, "Gen 4", 120.0, 150.0, lane = plan)
		b.add(2, "Free-birth bill", 142.2, lane = plan)
		b.add(3, "\"Hatchlings\"", 143.0, 144.0, lane = plan)
		b.add(4, "Gen 5 due", 150.0, lane = plan)
		b.add(5, "Firearms bill", 140.3, lane = politics)
		b.add(6, "Miner ban fails", 141.5, lane = politics)
		b.add(7, "Free-birth limit", 142.2, lane = politics)
		b.add(8, "Adriel campaigns", 143.5, 144.0, lane = politics)
		b.add(9, "Adriel elected", 144.0, lane = politics, milestone = true)
		b.add(10, "Miners barred, arrests", 144.4, lane = politics)
		b.add(11, "Free births banned", 144.8, lane = politics)
		b.add(12, "Firearms in production", 140.6, lane = tech)
		b.add(13, "Semiconductors", 141.3, lane = tech)
		b.add(14, "Microcontrollers", 142.4, lane = tech)
		b.add(15, "TV at the Root", 144.2, lane = tech)
		b.add(16, "Chapter 2 · Year 1", 140.0, 141.0)
		b.add(17, "Chapter 3 · Year 2", 141.0, 142.0)
		b.add(18, "Chapter 4 · Year 3", 142.0, 143.0)
		b.add(19, "Arc 4 (planned)", 143.0, 144.0)
		b.add(20, "Arc 5 (planned)", 144.0, 145.0)
		b.add(21, "Rowan intro", 140.1, kind = scene, sub = "Ch 2")
		b.add(22, "Group Intro", 140.12, kind = scene, sub = "Ch 2")
		b.add(23, "Radio Tech", 140.2, kind = scene, sub = "Ch 2")
		b.add(24, "Sports Game", 140.38, kind = scene, sub = "Ch 2")
		b.add(25, "First Hack", 140.45, kind = scene, sub = "Ch 2")
		b.add(26, "Post Hack", 140.62, kind = scene, sub = "Ch 2")
		b.add(27, "Astra's Garage", 141.1, kind = scene, sub = "Ch 3")
		b.add(28, "History, the Root", 141.2, kind = scene, sub = "Ch 3")
		b.add(29, "Caravan Intro", 141.42, kind = scene, sub = "Ch 3")
		b.add(30, "Racism at the bar", 141.43, kind = scene, sub = "Ch 3")
		b.add(31, "City trip (planned)", 142.3, 142.4, scene, sub = "Ch 4")
		return input(b.events, insurgencyLanes, landingYearTicks, phone, TimelineFrame.Story, selectedId = selectedId)
	}

	const val STACK_LANE = "Stack"

	/**
	 * One lane, five spans overlapping in a staircase. Span 2 alone outlasts every chain of the
	 * others, so it takes the band although span 1 starts first; the rest need two sub-columns.
	 */
	fun overlappingStack(phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		val b = Builder(HOUR.toDouble())
		b.add(1, "Early and short", 0.0, 2.0, lane = STACK_LANE)
		b.add(2, "The long one", 1.0, 16.0, lane = STACK_LANE)
		b.add(3, "Third step", 2.0, 6.0, lane = STACK_LANE)
		b.add(4, "Fourth step", 5.0, 9.0, lane = STACK_LANE)
		b.add(5, "Fifth step", 8.0, 14.0, lane = STACK_LANE)
		return input(b.events, listOf(STACK_LANE), dayClockTicks, phone, selectedId = selectedId)
	}

	/** [n] pseudo-random events over a year in six lanes, the same for every call. */
	fun stress(n: Int = 500, phone: Boolean = false, selectedId: Int? = null): TimelineLayoutInput {
		var seed = 0x5EED_1234_ABCDL
		fun next(bound: Long): Long {
			seed = seed * 6364136223846793005L + 1442695040888963407L
			return (seed ushr 33) % bound
		}

		val lanes = (1..6).map { "Lane $it" }
		val events = (0 until n).map { i ->
			val kind = when {
				i % 10 == 0 -> LayoutKind.SCENE
				i % 17 == 0 -> LayoutKind.ENTRY
				else -> LayoutKind.EVENT
			}
			val start = next(YEAR)
			val isSpan = next(3) == 0L
			val end = if (isSpan) start + 1 + next(10 * DAY) else null
			LayoutEvent(
				id = i + 1,
				kind = kind,
				title = "Event ${i + 1}" + if (i % 5 == 0) " with a longer title to wrap" else "",
				subtitle = if (kind == LayoutKind.SCENE) "Ch ${i / 10 + 1}" else null,
				lane = if (kind == LayoutKind.SCENE || i % 7 == 0) null else lanes[next(lanes.size.toLong()).toInt()],
				milestone = i % 97 == 0,
				order = i,
				start = start,
				end = end,
				precisionExtent = if (end == null && i % 23 == 0) DAY else 1,
			)
		}
		return input(events, lanes, dayClockTicks, phone, selectedId = selectedId)
	}
}
