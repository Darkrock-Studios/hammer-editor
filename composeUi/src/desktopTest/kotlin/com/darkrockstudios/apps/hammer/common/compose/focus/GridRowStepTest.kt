package com.darkrockstudios.apps.hammer.common.compose.focus

import androidx.compose.ui.input.key.Key
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GridRowStepTest {

	/** 3 columns, 8 items: rows 0 1 2 / 3 4 5 / 6 7. */
	private fun grid(current: Int, down: Boolean) = gridRowStep(current, count = 8, columns = 3, down = down)

	@Test
	fun `Down and Up in a grid move a whole row`() {
		assertEquals(4, grid(1, down = true))
		assertEquals(1, grid(4, down = false))
	}

	@Test
	fun `Down onto a shorter last row lands on its last item`() {
		assertEquals(7, grid(5, down = true))
	}

	@Test
	fun `a grid's first and last rows hold the cursor`() {
		assertEquals(2, grid(2, down = false))
		assertEquals(6, grid(6, down = true))
	}

	@Test
	fun `Left and Right step one item in a grid`() {
		val rowStep = GridRowStep { current, count, down -> gridRowStep(current, count, 3, down) }

		assertEquals(2, listCursorStep(1, 8, Key.DirectionRight, rowStep))
		assertEquals(0, listCursorStep(1, 8, Key.DirectionLeft, rowStep))
		assertEquals(4, listCursorStep(1, 8, Key.DirectionDown, rowStep))
	}

	/**
	 * Two lanes, everything on screen:
	 * lane 0: 0 (top 0), 2 (top 100), 4 (top 300)
	 * lane 1: 1 (top 0), 3 (top 50)
	 */
	private val lanes = listOf(
		LaneCell(index = 0, lane = 0, top = 0),
		LaneCell(index = 1, lane = 1, top = 0),
		LaneCell(index = 2, lane = 0, top = 100),
		LaneCell(index = 3, lane = 1, top = 50),
		LaneCell(index = 4, lane = 0, top = 300),
	)

	@Test
	fun `Down and Up in a staggered grid stay in the lane`() {
		assertEquals(2, staggeredRowStep(0, count = 5, down = true, shown = lanes))
		assertEquals(3, staggeredRowStep(1, count = 5, down = true, shown = lanes))
		assertEquals(2, staggeredRowStep(4, count = 5, down = false, shown = lanes))
	}

	@Test
	fun `at the end of a lane Down goes to the nearest item below in another lane`() {
		val shorter = lanes.filter { it.index != 4 } + LaneCell(index = 4, lane = 1, top = 200)

		assertEquals(4, staggeredRowStep(2, count = 5, down = true, shown = shorter))
		assertEquals(4, staggeredRowStep(4, count = 5, down = true, shown = shorter))
	}

	@Test
	fun `a lane's neighbour off screen falls back to one step`() {
		val firstRowOnly = lanes.take(2)

		assertEquals(2, staggeredRowStep(1, count = 5, down = true, shown = firstRowOnly))
		assertEquals(3, staggeredRowStep(4, count = 5, down = false, shown = emptyList()))
	}
}
