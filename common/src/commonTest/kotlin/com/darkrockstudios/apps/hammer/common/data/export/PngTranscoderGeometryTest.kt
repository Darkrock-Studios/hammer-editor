package com.darkrockstudios.apps.hammer.common.data.export

import kotlin.test.Test
import kotlin.test.assertEquals

class PngTranscoderGeometryTest {

	/** Where [orientationMatrix] maps the stored pixel corner ([x], [y]). */
	private fun FloatArray.map(x: Float, y: Float): Pair<Float, Float> =
		(this[0] * x + this[1] * y + this[2]) to (this[3] * x + this[4] * y + this[5])

	@Test
	fun `an image within the bound keeps its size`() {
		assertEquals(TranscodeSize(40, 20), transcodeSize(40, 20, orientation = 1, maxEdge = 2000))
	}

	@Test
	fun `a quarter turn swaps the edges`() {
		for (orientation in 5..8) {
			assertEquals(TranscodeSize(20, 40), transcodeSize(40, 20, orientation, maxEdge = 2000), "$orientation")
		}
	}

	@Test
	fun `a large image shrinks so its longer edge meets the bound`() {
		assertEquals(TranscodeSize(500, 2000), transcodeSize(8000, 2000, orientation = 6, maxEdge = 2000))
	}

	@Test
	fun `each orientation maps the stored top left corner to where it is seen`() {
		val stored = 40 to 20
		val expected = mapOf(
			1 to (0f to 0f),
			2 to (40f to 0f),
			3 to (40f to 20f),
			4 to (0f to 20f),
			5 to (0f to 0f),
			6 to (20f to 0f),
			7 to (20f to 40f),
			8 to (0f to 40f),
		)
		for ((orientation, corner) in expected) {
			val target = transcodeSize(stored.first, stored.second, orientation, maxEdge = 2000)
			val matrix = orientationMatrix(stored.first, stored.second, orientation, target)

			assertEquals(corner, matrix.map(0f, 0f), "orientation $orientation")
		}
	}

	@Test
	fun `a quarter clockwise turn maps the stored top right corner to the bottom right`() {
		val target = transcodeSize(40, 20, orientation = 6, maxEdge = 2000)

		assertEquals(20f to 40f, orientationMatrix(40, 20, 6, target).map(40f, 0f))
	}

	@Test
	fun `the matrix scales the image to the target`() {
		val target = TranscodeSize(100, 50)

		assertEquals(100f to 50f, orientationMatrix(400, 200, 1, target).map(400f, 200f))
	}
}
