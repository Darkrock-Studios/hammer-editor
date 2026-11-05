package timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelFitter
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabelFitterTest {
	private val measurer = MonospaceEstimateMeasurer()

	@Test
	fun `a roomy band wraps the title inside`() {
		val label = LabelFitter.fitBandLabel("The drawing room at night", 124f, 60f, measurer)

		val lines = assertIs<BandLabel.Lines>(label)
		assertEquals(listOf("The drawing", "room at night"), lines.lines)
		assertEquals(false, lines.small)
	}

	@Test
	fun `a short band falls back to one small line`() {
		val label = LabelFitter.fitBandLabel("Drawing room", 104f, 20f, measurer)

		val lines = assertIs<BandLabel.Lines>(label)
		assertEquals(listOf("Drawing room"), lines.lines)
		assertTrue(lines.small)
	}

	@Test
	fun `a narrow tall band rotates the title`() {
		val label = LabelFitter.fitBandLabel("Drawing room", 20f, 120f, measurer)

		val rotated = assertIs<BandLabel.Rotated>(label)
		assertEquals(14f, rotated.cx)
		assertEquals(60f, rotated.cy)
	}

	@Test
	fun `a band too small for any form gets no inside label`() {
		assertEquals(BandLabel.None, LabelFitter.fitBandLabel("Drawing room", 20f, 30f, measurer))
		assertEquals(BandLabel.None, LabelFitter.fitBandLabel("  ", 104f, 300f, measurer))
	}

	@Test
	fun `ellipsis never exceeds the width`() {
		val text = "Canterbury answers the Scopuli distress call"
		for (width in 0..300 step 3) {
			val fitted = LabelFitter.ellipsize(text, width.toFloat(), LabelStyle.Ledger, measurer)

			assertTrue(measurer.widthDp(fitted, LabelStyle.Ledger) <= width, "\"$fitted\" wider than $width")
			assertTrue(fitted == text || fitted.isEmpty() || fitted.endsWith(LabelFitter.ELLIPSIS))
		}
	}

	@Test
	fun `wrapped text keeps to two lines and the width`() {
		val text = "Canterbury answers the Scopuli distress call from deep in the belt"
		for (width in 6..300 step 7) {
			val lines = LabelFitter.wrapToWidth(text, width.toFloat(), LabelStyle.Ledger, measurer, 2)

			assertTrue(lines.size <= 2)
			lines.forEach { assertTrue(measurer.widthDp(it, LabelStyle.Ledger) <= width, "\"$it\" wider than $width") }
		}
	}

	@Test
	fun `a cut wrap ends in an ellipsis`() {
		val lines = LabelFitter.wrapToWidth("one two three four five six", 79f, LabelStyle.Ledger, measurer, 2)

		assertEquals(listOf("one two", "three fou…"), lines)
	}

	@Test
	fun `text that fits is left whole`() {
		val lines = LabelFitter.wrapToWidth("Dinner", 200f, LabelStyle.Ledger, measurer, 2)

		assertEquals(listOf("Dinner"), lines)
	}
}
