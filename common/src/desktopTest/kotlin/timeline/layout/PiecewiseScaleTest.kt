package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.buildScale
import com.darkrockstudios.apps.hammer.common.timeline.layout.buildSequenceScale
import com.darkrockstudios.apps.hammer.common.timeline.layout.medianGapPxPerUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiecewiseScaleTest {
	private val metrics = LayoutMetrics()

	@Test
	fun `y never decreases with position`() {
		val random = Random(7)
		repeat(20) {
			val points = LongArray(30) { random.nextLong(-10_000, 10_000) }.distinct().sorted().toLongArray()
			val spans = (0 until 8).map {
				val a = points[random.nextInt(points.size)]
				val b = points[random.nextInt(points.size)]
				minOf(a, b)..maxOf(a, b)
			}
			val scale = buildScale(points, spans, points.first(), points.last(), medianGapPxPerUnit(points, 40f), metrics)

			var previous = Float.NEGATIVE_INFINITY
			for (p in points.first()..points.last() step 37) {
				val y = scale.y(p)
				assertTrue(y >= previous, "y($p) = $y fell below $previous")
				previous = y
			}
		}
	}

	@Test
	fun `an empty stretch is capped at maxGap and folds when far longer`() {
		val points = longArrayOf(0, 10, 1000)

		val scale = buildScale(points, emptyList(), 0, 1000, pxPerUnit = 2.0, metrics = metrics)

		val (short, long) = scale.segments
		assertEquals(20f, short.y1 - short.y0)
		assertFalse(short.fold)
		assertEquals(metrics.maxGap, long.y1 - long.y0)
		assertTrue(long.fold)
	}

	@Test
	fun `a stretch a little over its cap is capped without folding`() {
		val points = longArrayOf(0, 25)

		val scale = buildScale(points, emptyList(), 0, 25, pxPerUnit = 2.0, metrics = metrics)

		val segment = scale.segments.single()
		assertEquals(metrics.maxGap, segment.y1 - segment.y0)
		assertFalse(segment.fold, "50dp natural is under 1.5 times the 40dp cap")
	}

	@Test
	fun `a stretch covered by a span may run four times longer`() {
		val points = longArrayOf(0, 70, 1000)

		val scale = buildScale(points, listOf(0L..1000L), 0, 1000, pxPerUnit = 2.0, metrics = metrics)

		val (first, second) = scale.segments
		assertEquals(140f, first.y1 - first.y0)
		assertFalse(first.fold)
		assertEquals(metrics.maxGap * metrics.coveredGapFactor, second.y1 - second.y0)
		assertTrue(second.fold)
	}

	@Test
	fun `a span covers only the stretches inside it`() {
		val points = longArrayOf(0, 100, 200, 300)

		val scale = buildScale(points, listOf(100L..200L), 0, 300, pxPerUnit = 1.0, metrics = metrics)

		val heights = scale.segments.map { it.y1 - it.y0 }
		assertEquals(listOf(40f, 100f, 40f), heights)
	}

	@Test
	fun `the scale starts at padTop and its height adds padBottom`() {
		val scale = buildScale(longArrayOf(0, 10), emptyList(), 0, 10, 1.0, metrics)

		assertEquals(metrics.padTop, scale.y(0))
		assertEquals(metrics.padTop + 10f, scale.y(10))
		assertEquals(metrics.padTop + 10f + metrics.padBottom, scale.height)
		assertEquals(metrics.padTop, scale.y(-50), "before the frame clamps to the top")
	}

	@Test
	fun `pixels per unit put the median gap at maxGap`() {
		assertEquals(20.0, medianGapPxPerUnit(longArrayOf(0, 1, 3, 6), 40f))
		assertEquals(16.0, medianGapPxPerUnit(longArrayOf(0, 1, 3, 6, 10), 40f), "even count averages the middle two")
	}

	@Test
	fun `sequence rows are uniform and never fold`() {
		val points = longArrayOf(-5_000_000, 0, 3, 4, 9_000_000)

		val scale = buildSequenceScale(points, metrics)

		val ys = points.map { scale.y(it) }
		ys.zipWithNext().forEach { (a, b) -> assertEquals(metrics.sequenceRowStep, b - a) }
		assertTrue(scale.segments.none { it.fold })
	}
}
