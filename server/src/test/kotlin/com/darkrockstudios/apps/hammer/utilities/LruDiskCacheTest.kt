package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class LruDiskCacheTest {

	@TempDir
	lateinit var dir: Path

	private fun cache(maxBytes: Long) = LruDiskCache(dir, maxBytes)

	private fun age(cache: LruDiskCache, key: String, at: Instant) {
		Files.setLastModifiedTime(cache.fileFor(key), FileTime.from(at))
	}

	@Test
	fun `put then get returns the stored bytes`() {
		val c = cache(1_000)
		c.put("k", byteArrayOf(1, 2, 3))
		assertContentEquals(byteArrayOf(1, 2, 3), c.get("k"))
	}

	@Test
	fun `get returns null for a missing key`() {
		assertNull(cache(1_000).get("missing"))
	}

	@Test
	fun `keys with filesystem-unsafe characters are hashed to a valid filename`() {
		val c = cache(1_000)
		val key = "a/b c:\\d?e"
		c.put(key, byteArrayOf(9))
		assertContentEquals(byteArrayOf(9), c.get(key))
	}

	@Test
	fun `getOrPut computes only on a miss`() {
		val c = cache(1_000)
		var computeCount = 0
		val first = c.getOrPut("k") { computeCount++; byteArrayOf(7) }
		val second = c.getOrPut("k") { computeCount++; byteArrayOf(7) }
		assertEquals(1, computeCount)
		assertContentEquals(byteArrayOf(7), first)
		assertContentEquals(byteArrayOf(7), second)
	}

	@Test
	fun `put evicts the least-recently-used entry when over capacity`() {
		val c = cache(maxBytes = 250)
		c.put("a", ByteArray(100))
		c.put("b", ByteArray(100))
		age(c, "a", Instant.ofEpochMilli(1_000))
		age(c, "b", Instant.ofEpochMilli(2_000))
		c.put("c", ByteArray(100)) // 300 > 250 -> evict oldest (a)
		assertNull(c.get("a"))
		assertNotNull(c.get("b"))
		assertNotNull(c.get("c"))
	}

	@Test
	fun `a cache hit refreshes recency so an older entry is evicted instead`() {
		val c = cache(maxBytes = 250)
		c.put("a", ByteArray(100))
		c.put("b", ByteArray(100))
		age(c, "a", Instant.ofEpochMilli(1_000))
		age(c, "b", Instant.ofEpochMilli(2_000))
		c.get("a") // touches a -> now the most recent
		c.put("c", ByteArray(100)) // evict oldest -> b
		assertNotNull(c.get("a"))
		assertNull(c.get("b"))
		assertNotNull(c.get("c"))
	}

	@Test
	fun `the newest entry is never evicted even if it alone exceeds the cap`() {
		val c = cache(maxBytes = 50)
		c.put("big", ByteArray(100))
		assertNotNull(c.get("big"))
	}

	@Test
	fun `prune with a max age deletes entries older than the cutoff`() {
		val c = cache(maxBytes = 10_000)
		c.put("old", byteArrayOf(1))
		c.put("fresh", byteArrayOf(2))
		age(c, "old", Instant.now().minus(java.time.Duration.ofDays(40)))
		age(c, "fresh", Instant.now())

		c.prune(maxAge = kotlin.time.Duration.parse("30d"))

		assertNull(c.get("old"))
		assertNotNull(c.get("fresh"))
	}

	@Test
	fun `prune enforces the size bound over entries written behind its back`() {
		val c = cache(maxBytes = 150)
		// Write straight into the cache dir, bypassing put()'s auto-prune, to simulate drift
		// that a scheduled maintenance job would later reconcile.
		Files.write(c.fileFor("a"), ByteArray(100))
		Files.write(c.fileFor("b"), ByteArray(100))
		age(c, "a", Instant.ofEpochMilli(1_000))
		age(c, "b", Instant.ofEpochMilli(2_000))

		c.prune() // 200 > 150 -> evict least-recently-used (a)

		assertNull(c.get("a"))
		assertNotNull(c.get("b"))
	}
}
