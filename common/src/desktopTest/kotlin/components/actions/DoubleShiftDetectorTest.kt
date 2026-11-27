package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.DoubleShiftDetector
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoubleShiftDetectorTest {

	private val detector = DoubleShiftDetector()

	private fun shiftDown(at: Long) = detector.onKey(isShift = true, isDown = true, atMs = at)
	private fun shiftUp(at: Long) = detector.onKey(isShift = true, isDown = false, atMs = at)
	private fun other(at: Long) = detector.onKey(isShift = false, isDown = true, atMs = at)

	/** Every result a tap sequence produced, so a test sees exactly which event fired. */
	private fun taps(vararg events: Pair<Boolean, Long>): List<Boolean> =
		events.map { (down, at) -> if (down) shiftDown(at) else shiftUp(at) }

	@Test
	fun `two quick taps fire on the second release`() {
		val results = taps(true to 0, false to 80, true to 200, false to 280)

		assertEquals(listOf(false, false, false, true), results)
	}

	@Test
	fun `taps too far apart do not fire`() {
		val results = taps(true to 0, false to 80, true to 600, false to 680)

		assertFalse(results.any { it })
	}

	@Test
	fun `a key between the taps starts over`() {
		shiftDown(0)
		shiftUp(80)
		other(120)
		shiftDown(200)

		assertFalse(shiftUp(280))
	}

	@Test
	fun `typing a capital between taps starts over`() {
		shiftDown(0)
		other(40)
		shiftUp(80)
		shiftDown(200)

		assertFalse(shiftUp(280))
	}

	@Test
	fun `a held Shift is not a tap`() {
		shiftDown(0)
		shiftUp(500)
		shiftDown(600)

		assertFalse(shiftUp(680))
	}

	@Test
	fun `a repeating held Shift is not a tap`() {
		shiftDown(0)
		shiftDown(30)
		shiftUp(100)
		shiftDown(200)

		assertFalse(shiftUp(280))
	}

	@Test
	fun `a pointer press between taps starts over`() {
		shiftDown(0)
		shiftUp(80)
		detector.reset()
		shiftDown(200)

		assertFalse(shiftUp(280))
	}

	@Test
	fun `a third tap after firing needs a fourth to fire again`() {
		taps(true to 0, false to 80, true to 200, false to 280)
		shiftDown(400)

		assertFalse(shiftUp(480))
		shiftDown(600)
		assertTrue(shiftUp(680))
	}
}
