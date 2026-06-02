package com.darkrockstudios.apps.hammer.base.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OffsetMapTest {

	@Test
	fun `identity mapping when both sides share offsets`() {
		val map = OffsetMap(listOf(DiffAnchor(0, 0), DiffAnchor(100, 100)))
		assertEquals(0, map.leftToRight(0))
		assertEquals(50, map.leftToRight(50))
		assertEquals(100, map.leftToRight(100))
	}

	@Test
	fun `offsets before first anchor clamp to first`() {
		val map = OffsetMap(listOf(DiffAnchor(10, 20), DiffAnchor(100, 100)))
		assertEquals(20, map.leftToRight(0))
		assertEquals(20, map.leftToRight(-5))
	}

	@Test
	fun `offsets past last anchor clamp to last`() {
		val map = OffsetMap(listOf(DiffAnchor(0, 0), DiffAnchor(100, 80)))
		assertEquals(80, map.leftToRight(100))
		assertEquals(80, map.leftToRight(999))
	}

	@Test
	fun `interpolates proportionally across an edit hunk`() {
		// Left grows from 0..20 while right grows 0..40 across one hunk: midpoint maps to midpoint.
		val map = OffsetMap(listOf(DiffAnchor(0, 0), DiffAnchor(20, 40)))
		assertEquals(20, map.leftToRight(10))
		assertEquals(10, map.rightToLeft(20))
	}

	@Test
	fun `equal region maps one to one between edit hunks`() {
		// Anchors: edit shifts right by 5 chars, then an equal region of length 50.
		val map = OffsetMap(
			listOf(
				DiffAnchor(0, 0),
				DiffAnchor(10, 10),   // start of hunk
				DiffAnchor(10, 15),   // end of hunk (right gained 5 chars)
				DiffAnchor(60, 65),   // end of following equal region (both +50)
			)
		)
		// Inside the equal region, left 35 -> right 40 (constant +5 shift).
		assertEquals(40, map.leftToRight(35))
	}

	@Test
	fun `round trips are stable for monotonic anchors`() {
		val map = OffsetMap(
			listOf(
				DiffAnchor(0, 0),
				DiffAnchor(30, 25),
				DiffAnchor(80, 90),
				DiffAnchor(120, 120),
			)
		)
		for (left in 0..120 step 7) {
			val right = map.leftToRight(left)
			assertTrue(right in 0..120, "mapped right offset $right out of range for left $left")
		}
	}

	@Test
	fun `empty anchors degrade to zero mapping`() {
		val map = OffsetMap(emptyList())
		assertEquals(0, map.leftToRight(42))
		assertEquals(0, map.rightToLeft(42))
	}
}
