package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertTrue

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
	fun `getOrPut computes a hot key once under concurrent misses`() {
		val c = cache(1_000_000)
		val computeCount = java.util.concurrent.atomic.AtomicInteger(0)
		val start = java.util.concurrent.CountDownLatch(1)
		val threadCount = 16
		val results = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
		val threads = List(threadCount) {
			Thread {
				start.await()
				val bytes = c.getOrPut("hot") {
					computeCount.incrementAndGet()
					Thread.sleep(50) // hold the compute so racers pile up on the lock
					byteArrayOf(7)
				}
				results.add(bytes)
			}
		}
		threads.forEach { it.start() }
		start.countDown()
		threads.forEach { it.join() }

		assertEquals(1, computeCount.get(), "a hot key should be computed exactly once")
		assertEquals(threadCount, results.size)
		results.forEach { assertContentEquals(byteArrayOf(7), it) }
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
	fun `getOrPutSuspending computes a hot key once under concurrent misses`() = runBlocking {
		val c = cache(1_000_000)
		val computeCount = java.util.concurrent.atomic.AtomicInteger(0)
		val callers = 16

		val results = (1..callers).map {
			async(Dispatchers.IO) {
				c.getOrPutSuspending("hot") {
					computeCount.incrementAndGet()
					delay(50) // hold the compute so racers pile up on the mutex
					byteArrayOf(7)
				}
			}
		}.awaitAll()

		assertEquals(1, computeCount.get(), "a hot key should be computed exactly once")
		assertEquals(callers, results.size)
		results.forEach { assertContentEquals(byteArrayOf(7), it) }
	}

	@Test
	fun `getOrPutSuspending computes only on a miss`() = runBlocking {
		val c = cache(1_000)
		var computeCount = 0
		val first = c.getOrPutSuspending("k") { computeCount++; byteArrayOf(7) }
		val second = c.getOrPutSuspending("k") { computeCount++; byteArrayOf(7) }
		assertEquals(1, computeCount)
		assertContentEquals(byteArrayOf(7), first)
		assertContentEquals(byteArrayOf(7), second)
	}

	@Test
	fun `getOrPutSuspending does not store a null value`() = runBlocking {
		val c = cache(1_000)

		val value = c.getOrPutSuspending("k") { null }

		assertNull(value)
		assertNull(c.get("k"), "a null compute must leave the cache empty")
	}

	@Test
	fun `a value is still returned when the cache cannot be written`() = runBlocking {
		val cacheDir = dir.resolve("evaporating")
		val c = LruDiskCache(cacheDir, 1_000)
		// Pull the directory out from under the cache so every write fails.
		Files.delete(cacheDir)

		val computed = c.getOrPutSuspending("k") { byteArrayOf(1, 2, 3) }
		val direct = runCatching { c.put("k", byteArrayOf(4)) }

		assertContentEquals(byteArrayOf(1, 2, 3), computed, "a failed write must not fail the caller")
		assertTrue(direct.isSuccess, "put must swallow IO failures, was ${direct.exceptionOrNull()}")
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
