package repositories.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimatePages
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimateReadingMinutes
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReadingEstimateTest {

	@Test
	fun `zero words is zero minutes and zero pages`() {
		assertEquals(0, estimateReadingMinutes(0))
		assertEquals(0, estimatePages(0))
	}

	@Test
	fun `negative words is zero`() {
		assertEquals(0, estimateReadingMinutes(-100))
		assertEquals(0, estimatePages(-100))
	}

	@Test
	fun `tiny non-zero word counts round up to one`() {
		assertEquals(1, estimateReadingMinutes(1))
		assertEquals(1, estimatePages(1))
	}

	@Test
	fun `reading minutes round up partial minutes`() {
		// 23,214 words at 225 wpm = 103.17 min -> 104
		assertEquals(104, estimateReadingMinutes(23_214))
	}

	@Test
	fun `pages round up partial pages`() {
		// 23,214 words at 300 wpp = 77.38 pages -> 78
		assertEquals(78, estimatePages(23_214))
	}

	@Test
	fun `custom wpm overrides default`() {
		assertEquals(10, estimateReadingMinutes(2_000, wpm = 200))
	}

	@Test
	fun `custom wpp overrides default`() {
		assertEquals(8, estimatePages(2_000, wpp = 250))
	}
}
