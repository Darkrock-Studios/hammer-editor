import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginCache
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PluginCacheTest {

	private var now = Instant.fromEpochSeconds(0)
	private val fileSystem = FakeFileSystem(object : Clock {
		override fun now() = now
	})
	private val directory = "/cache/plugins/style".toPath()

	private fun key(text: String) = text.encodeToByteArray()

	@Test
	fun `values are kept until removed`() {
		val cache = PluginCache(fileSystem, directory)
		assertNull(cache.get(key("a")))

		cache.set(key("a"), byteArrayOf(1, 2))
		cache.set(key("../b"), byteArrayOf(3))

		assertContentEquals(byteArrayOf(1, 2), cache.get(key("a")))
		assertContentEquals(byteArrayOf(3), PluginCache(fileSystem, directory).get(key("../b")))
		assertEquals(2, fileSystem.list(directory).size)

		cache.set(key("a"), null)
		assertNull(cache.get(key("a")))
	}

	@Test
	fun `past the limit the oldest written are dropped`() {
		val cache = PluginCache(fileSystem, directory, maxBytes = 40)
		for (name in listOf("a", "b", "c", "d")) {
			cache.set(key(name), ByteArray(10))
			now += 1.seconds
		}
		cache.set(key("a"), ByteArray(10))

		cache.set(key("e"), ByteArray(10))

		assertNull(cache.get(key("b")))
		assertNull(cache.get(key("c")))
		listOf("d", "a", "e").forEach { assertEquals(10, cache.get(key(it))?.size) }
	}

	@Test
	fun `a staging file left behind is dropped with the oldest`() {
		val cache = PluginCache(fileSystem, directory, maxBytes = 40)
		fileSystem.createDirectories(directory)
		fileSystem.write(directory / "left-behind.partial") { write(ByteArray(10)) }
		now += 1.seconds
		for (name in listOf("a", "b", "c")) cache.set(key(name), ByteArray(10))

		cache.set(key("d"), ByteArray(10))

		assertEquals(false, fileSystem.exists(directory / "left-behind.partial"))
	}

	@Test
	fun `keys and values over their limits are refused`() {
		val cache = PluginCache(fileSystem, directory)
		assertThrows<IllegalArgumentException> { cache.set(ByteArray(PluginCache.MAX_KEY_BYTES + 1), byteArrayOf(1)) }
		assertThrows<IllegalArgumentException> { cache.set(key("a"), ByteArray(PluginCache.MAX_VALUE_BYTES + 1)) }
	}

	@Test
	fun `clearing removes everything`() {
		val cache = PluginCache(fileSystem, directory)
		cache.set(key("a"), byteArrayOf(1))

		cache.clear()

		assertNull(cache.get(key("a")))
		assertEquals(false, fileSystem.exists(directory))
	}
}
