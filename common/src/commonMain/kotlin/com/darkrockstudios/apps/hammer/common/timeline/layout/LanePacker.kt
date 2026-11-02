package com.darkrockstudios.apps.hammer.common.timeline.layout

/**
 * A span to pack, drawn from [y0] to [y1]. [weight] is its duration in position units and
 * [start] its start position, used to break ties between equally long band sets.
 */
data class PackItem(val y0: Float, val y1: Float, val weight: Long, val start: Long)

/** [columns] holds each item's column, by input index; column 0 is the band. */
class LanePacking(val columns: IntArray, val columnCount: Int)

/**
 * Packs one lane's spans into columns. Column 0, the band, takes the set of mutually
 * non-overlapping spans with the greatest total duration (weighted interval scheduling); ties go
 * to the set that starts earlier. Every other span goes first-fit, in start order, into
 * sub-columns from 1. Spans that only touch (one ends where the next starts) share a column.
 * Point marks are not packed: they always sit in column 0.
 */
object LanePacker {
	fun pack(items: List<PackItem>): LanePacking {
		val n = items.size
		val columns = IntArray(n) { -1 }
		if (n == 0) return LanePacking(columns, 0)

		val byEnd = (0 until n).sortedWith(
			compareBy<Int>({ items[it].y1 }, { items[it].y0 }, { it })
		)
		// prev[j]: how many of the first j items in end order end at or before item j starts.
		val prev = IntArray(n)
		for (j in 0 until n) {
			val start = items[byEnd[j]].y0
			var lo = 0
			var hi = j
			while (lo < hi) {
				val mid = (lo + hi) ushr 1
				if (items[byEnd[mid]].y1 <= start) lo = mid + 1 else hi = mid
			}
			prev[j] = lo
		}

		// best*[k] describes the best band among the first k items in end order.
		val bestTotal = LongArray(n + 1)
		val bestFirst = LongArray(n + 1) { Long.MAX_VALUE }
		val take = BooleanArray(n)
		for (j in 0 until n) {
			val item = items[byEnd[j]]
			val p = prev[j]
			val withTotal = bestTotal[p] + item.weight
			val withFirst = if (bestFirst[p] == Long.MAX_VALUE) item.start else bestFirst[p]
			val better = withTotal > bestTotal[j] || (withTotal == bestTotal[j] && withFirst < bestFirst[j])
			if (better) {
				bestTotal[j + 1] = withTotal
				bestFirst[j + 1] = withFirst
				take[j] = true
			} else {
				bestTotal[j + 1] = bestTotal[j]
				bestFirst[j + 1] = bestFirst[j]
			}
		}
		var k = n
		while (k > 0) {
			if (take[k - 1]) {
				columns[byEnd[k - 1]] = 0
				k = prev[k - 1]
			} else {
				k--
			}
		}

		val byStart = (0 until n).filter { columns[it] < 0 }.sortedWith(
			compareBy<Int>({ items[it].y0 }, { -(items[it].y1 - items[it].y0) }, { it })
		)
		val bottoms = ArrayList<Float>()
		for (i in byStart) {
			val item = items[i]
			var placed = false
			for (c in bottoms.indices) {
				if (bottoms[c] <= item.y0) {
					columns[i] = c + 1
					bottoms[c] = item.y1
					placed = true
					break
				}
			}
			if (!placed) {
				bottoms += item.y1
				columns[i] = bottoms.size
			}
		}
		return LanePacking(columns, 1 + bottoms.size)
	}
}
