package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutKind
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerCandidate
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerMark
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerPlacer
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.PlacedLedgerRow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerPlacerTest {
	private val measurer = MonospaceEstimateMeasurer()
	private val metrics = LayoutMetrics()
	private val epsilon = 1e-3f

	private val titles = listOf("Dinner", "The wine key", "Canterbury answers the Scopuli", "A", "Miller finds Julie in the hotel")

	private fun candidate(id: Int, anchor: Float, pinned: Boolean = false, order: Int = id, sub: String? = null) =
		LedgerCandidate(
			eventId = id,
			kind = if (pinned) LayoutKind.EVENT else LayoutKind.SCENE,
			anchorY = anchor,
			anchorY1 = null,
			title = titles[id % titles.size],
			sub = sub,
			style = if (pinned) LedgerStyle.Milestone else LedgerStyle.Muted,
			mark = if (pinned) LedgerMark.MilestoneDiamond else LedgerMark.Pilcrow,
			laneIndex = null,
			pinned = pinned,
			order = order,
		)

	/** Unpinned rows crowded together, with pinned rows at least a full row apart from each other. */
	private fun randomCandidates(random: Random): List<LedgerCandidate> {
		val out = ArrayList<LedgerCandidate>()
		var pinnedAnchor = random.nextFloat() * 50f
		var id = 0
		while (pinnedAnchor < 1500f) {
			out += candidate(id++, pinnedAnchor, pinned = true, sub = if (random.nextBoolean()) "Ch 3" else null)
			pinnedAnchor += 40f + random.nextFloat() * 200f
		}
		repeat(random.nextInt(5, 60)) {
			out += candidate(id++, random.nextFloat() * 1500f, sub = if (random.nextBoolean()) "Ch 1 · the opening" else null)
		}
		return out.shuffled(random)
	}

	private fun place(candidates: List<LedgerCandidate>, width: Float = 80f): List<PlacedLedgerRow> =
		LedgerPlacer.place(candidates, width, measurer, metrics)

	@Test
	fun `rows never overlap`() {
		val random = Random(3)
		repeat(200) {
			val rows = place(randomCandidates(random)).sortedBy { it.top }

			rows.zipWithNext().forEach { (a, b) ->
				assertTrue(b.top >= a.bottom - epsilon, "row ${a.candidate.eventId} [${a.top}, ${a.bottom}) overlaps ${b.candidate.eventId} at ${b.top}")
			}
		}
	}

	@Test
	fun `milestones sit just below their anchor`() {
		val random = Random(5)
		repeat(200) {
			val rows = place(randomCandidates(random))

			rows.filter { it.candidate.pinned }.forEach { row ->
				assertEquals(row.candidate.anchorY + 4f, row.textY, epsilon)
			}
		}
	}

	@Test
	fun `unpinned rows flow around pinned ones`() {
		val random = Random(9)
		repeat(200) {
			val rows = place(randomCandidates(random))
			val pinned = rows.filter { it.candidate.pinned }

			rows.filterNot { it.candidate.pinned }.forEach { row ->
				pinned.forEach { pin ->
					assertTrue(row.top >= pin.bottom - epsilon || row.bottom <= pin.top + epsilon, "row ${row.candidate.eventId} intersects milestone ${pin.candidate.eventId}")
				}
				assertTrue(row.textY >= row.candidate.anchorY + 4f - epsilon, "row ${row.candidate.eventId} rose above its anchor")
			}
		}
	}

	@Test
	fun `unpinned rows keep their anchor order`() {
		val random = Random(13)
		repeat(200) {
			val candidates = randomCandidates(random)

			val unpinned = place(candidates).filterNot { it.candidate.pinned }

			val byPlacement = unpinned.sortedBy { it.textY }.map { it.candidate.eventId }
			val byAnchor = unpinned.sortedWith(compareBy({ it.candidate.anchorY }, { it.candidate.order })).map { it.candidate.eventId }
			assertEquals(byAnchor, byPlacement)
		}
	}

	@Test
	fun `a milestone crowded by an earlier one is pushed below it`() {
		val rows = place(listOf(candidate(1, 100f, pinned = true), candidate(2, 105f, pinned = true)))

		val (first, second) = rows
		assertEquals(104f, first.textY, epsilon)
		assertEquals(first.bottom, second.top, epsilon)
	}

	@Test
	fun `row height counts title lines, the subtitle and padding`() {
		val rows = place(listOf(candidate(2, 0f, sub = "Ch 1"), candidate(3, 500f)), width = 80f)

		val (wrapped, single) = rows
		assertEquals(2, wrapped.lines.size)
		assertEquals(2 * 11f + 11f + 4f, wrapped.height)
		assertEquals(11f + 4f, single.height)
	}

	@Test
	fun `ledger width follows the widest fitted line within its bounds`() {
		assertEquals(96f, LedgerPlacer.ledgerWidth(listOf(candidate(3, 0f)), measurer, metrics))
		// "Canterbury answers the Scopuli" wraps at 82dp to "Canterbury" and "answers the…".
		val wrapped = measurer.widthDp("answers the…", LabelStyle.Ledger)
		assertEquals(metrics.ledgerPad + wrapped, LedgerPlacer.ledgerWidth(listOf(candidate(2, 0f)), measurer, metrics), epsilon)
		val subWidth = measurer.widthDp("Ch 3 · Pike t", LabelStyle.LedgerSub)
		assertEquals(metrics.ledgerPad + subWidth, LedgerPlacer.ledgerWidth(listOf(candidate(0, 0f, sub = "Ch 3 · Pike t")), measurer, metrics), epsilon)
	}

	@Test
	fun `a long subtitle is measured as ellipsized`() {
		val sub = "Chapter 12 · Holden and Naomi on the Rocinante"

		val width = LedgerPlacer.ledgerWidth(listOf(candidate(3, 0f, sub = sub)), measurer, metrics)

		val fitted = measurer.widthDp(sub.take(14) + "…", LabelStyle.LedgerSub)
		assertEquals(metrics.ledgerPad + fitted, width, epsilon)
	}
}
