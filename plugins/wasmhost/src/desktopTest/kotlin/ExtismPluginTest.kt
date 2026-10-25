import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ModuleLimiter
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtismPluginTest {

	@Test
	fun `transforms its input into its output`() {
		val plugin = ExtismPlugin(testPlugin("upper"))

		val output = plugin.call("run", "Call me Ishmael.".encodeToByteArray(), FUEL)

		assertEquals("CALL ME ISHMAEL.", output.decodeToString())
	}

	@Test
	fun `each call starts from empty memory`() {
		val plugin = ExtismPlugin(testPlugin("upper"))

		plugin.call("run", "a long first input".encodeToByteArray(), FUEL)

		assertEquals("B", plugin.call("run", "b".encodeToByteArray(), FUEL).decodeToString())
	}

	@Test
	fun `user functions receive and return kernel blocks`() {
		val reverse = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) { args ->
			write(read(args[0]).reversedArray())
		}
		val plugin = ExtismPlugin(testPlugin("dispatch_echo"), listOf(reverse))

		assertEquals("cba", plugin.call("run", "abc".encodeToByteArray(), FUEL).decodeToString())
	}

	@Test
	fun `imports the host does not provide fail the load`() {
		val error = assertThrows<PluginException> { ExtismPlugin(testPlugin("wasi")) }
		assertTrue("wasi_snapshot_preview1" in error.message!!)
	}

	@Test
	fun `errors the plugin sets are reported`() {
		val plugin = ExtismPlugin(testPlugin("fail"))

		val error = assertThrows<PluginException> { plugin.call("run", ByteArray(0), FUEL) }
		assertTrue(error.message!!.endsWith(": no"))
	}

	@Test
	fun `an error set before a trap is reported`() {
		val plugin = ExtismPlugin(testPlugin("fail"))

		val error = assertThrows<PluginException> { plugin.call("abort", ByteArray(0), FUEL) }
		assertTrue(": no (" in error.message!!)
	}

	@Test
	fun `HTTP is refused`() {
		val plugin = ExtismPlugin(testPlugin("fail"))

		val error = assertThrows<PluginException> { plugin.call("fetch", ByteArray(0), FUEL) }
		assertTrue("HTTP" in error.message!!)
	}

	@Test
	fun `a module that never returns runs out of fuel`() {
		val plugin = ExtismPlugin(testPlugin("spin"))

		val error = assertThrows<PluginException> { plugin.call("run", ByteArray(0), 100_000) }
		assertTrue("out of fuel" in error.message!!)
	}

	@Test
	fun `a start function that never returns runs out of fuel`() {
		val error = assertThrows<PluginException> { ExtismPlugin(testPlugin("start_spin")) }
		assertEquals("Plugin ran out of fuel starting up", error.message)
	}

	@Test
	fun `fuel is counted per loop iteration and call`() {
		val plugin = ExtismPlugin(testPlugin("count"))

		plugin.call("run", 1_000L.toLittleEndian(), 1_002)
		assertEquals(0, plugin.remainingFuel())
		assertThrows<PluginException> { plugin.call("run", 1_000L.toLittleEndian(), 1_001) }
	}

	@Test
	fun `another thread can interrupt a running call`() {
		val plugin = ExtismPlugin(testPlugin("spin"))
		var error: PluginException? = null
		val call = thread { error = runCatching { plugin.call("run", ByteArray(0), Long.MAX_VALUE) }.exceptionOrNull() as? PluginException }

		while (!plugin.interrupt()) Thread.sleep(1)
		call.join(5_000)
		assertEquals("Plugin was interrupted in run", error?.message)
		assertFalse(plugin.interrupt())
	}

	@Test
	fun `GC objects a plugin keeps cannot grow past the cap`() {
		val plugin = ExtismPlugin(testPlugin("gc_heap"), maxGuestHeapBytes = 4L * 1024 * 1024)

		val error = assertThrows<PluginException> { plugin.call("hoard", ByteArray(0), FUEL) }
		assertEquals("Plugin ran out of memory in hoard", error.message)
	}

	@Test
	fun `GC objects a plugin keeps may take up to half the host heap`() {
		// The reading level report on a 100,000-word novel needs 192 MiB, half a 384 MB phone heap.
		val host = Runtime.getRuntime().maxMemory()
		val plugin = ExtismPlugin(testPlugin("gc_heap"), maxGuestHeapBytes = host)

		assertThrows<PluginException> { plugin.call("hoard", ByteArray(0), FUEL) }
		assertTrue(plugin.guestHeapBytes > host * 2 / 5, "${plugin.guestHeapBytes / MIB} MiB of ${host / MIB}")
	}

	@Test
	fun `GC objects a call leaves behind are freed when it returns`() {
		// Each call allocates about 10 MiB and keeps none of it: twenty calls far outrun the cap.
		val plugin = ExtismPlugin(testPlugin("gc_heap"), maxGuestHeapBytes = 16L * 1024 * 1024)

		repeat(20) { plugin.call("churn", ByteArray(0), FUEL) }
	}

	@Test
	fun `linear memory cannot grow past the cap`() {
		val plugin = ExtismPlugin(testPlugin("count"), limiter = ModuleLimiter(maxMemoryPages = 16))

		// memory.grow answers -1 when refused; the export returns it as a failure code.
		val error = assertThrows<PluginException> { plugin.call("grow", ByteArray(0), FUEL) }
		assertTrue("code -1" in error.message!!)
	}

	@Test
	fun `tables cannot grow past the cap`() {
		val plugin = ExtismPlugin(testPlugin("table"))

		val error = assertThrows<PluginException> { plugin.call("grow", ByteArray(0), FUEL) }
		assertTrue("code -1" in error.message!!)
	}

	@Test
	fun `variables past the byte cap trap, and the next call runs`() {
		val plugin = ExtismPlugin(testPlugin("limits"))

		val error = assertThrows<PluginException> { plugin.call("vars", args(64, 64 * KIB), FUEL) }

		assertTrue("var_set" in error.message!!, error.message)
		plugin.call("vars", args(1, 1), FUEL)
	}

	@Test
	fun `variables past the count cap trap, and the next call runs`() {
		val plugin = ExtismPlugin(testPlugin("limits"))

		val error = assertThrows<PluginException> { plugin.call("vars", args(2_000, 1), FUEL) }

		assertTrue("var_set" in error.message!!, error.message)
		plugin.call("vars", args(1, 1), FUEL)
	}

	@Test
	fun `a long log message is cut short`() {
		val logged = mutableListOf<String>()
		val plugin = ExtismPlugin(testPlugin("limits"), log = { _, message -> logged += message })

		plugin.call("log", args(MIB, 1), FUEL)

		assertEquals(1, logged.size)
		assertTrue(logged.single().length <= 8 * KIB, "${logged.single().length} chars")
	}

	@Test
	fun `a call's log output is capped, and the next call logs again`() {
		val logged = mutableListOf<String>()
		val plugin = ExtismPlugin(testPlugin("limits"), log = { _, message -> logged += message })

		plugin.call("log", args(100, 10_000), FUEL)

		assertTrue(logged.sumOf { it.length } <= 128 * KIB, "${logged.sumOf { it.length }} chars")
		assertTrue("dropped" in logged.last(), logged.last())
		logged.clear()
		plugin.call("log", args(100, 1), FUEL)
		assertEquals(1, logged.size)
	}

	@Test
	fun `the kernel cannot grow past its cap, and the next call runs`() {
		val plugin = ExtismPlugin(testPlugin("limits"), maxKernelBytes = MIB)

		val error = assertThrows<PluginException> { plugin.call("alloc", args(MIB + 1), FUEL) }

		assertTrue("alloc" in error.message!!, error.message)
		plugin.call("alloc", args(MIB / 2), FUEL)
	}

	@Test
	fun `the kernel takes at most a sixteenth of the host heap`() {
		val share = Runtime.getRuntime().maxMemory() / 16
		val plugin = ExtismPlugin(testPlugin("limits"), maxKernelBytes = share * 2)

		assertThrows<PluginException> { plugin.call("alloc", args(share + 1), FUEL) }
	}

	@Test
	fun `running out of memory in a host function fails the call, not the host`() {
		val hoard = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) {
			throw OutOfMemoryError("Java heap space")
		}
		val plugin = ExtismPlugin(testPlugin("dispatch_echo"), listOf(hoard))

		val error = assertThrows<PluginException> { plugin.call("run", "abc".encodeToByteArray(), FUEL) }

		assertEquals("Plugin ran out of memory in run", error.message)
		assertTrue(plugin.outOfMemory)
		// Its interpreter state may be torn, so it refuses further calls rather than running on it.
		assertThrows<PluginException> { plugin.call("run", "abc".encodeToByteArray(), FUEL) }
	}

	@Test
	fun `running out of memory starting up fails the load, not the host`() {
		val error = assertThrows<PluginException> { ExtismPlugin(loadWasm = { throw OutOfMemoryError("Java heap space") }) }

		assertEquals("Plugin ran out of memory starting up", error.message)
	}

	private fun args(vararg values: Long): ByteArray = values.fold(ByteArray(0)) { bytes, value -> bytes + value.toLittleEndian() }

	private companion object {
		const val FUEL = 10_000_000L
		const val KIB = 1024L
		const val MIB = 1024L * 1024
	}
}
