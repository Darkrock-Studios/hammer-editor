import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.FuelInstrumenter
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
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
	fun `fuel is counted per loop iteration and call`() {
		val plugin = ExtismPlugin(testPlugin("count"))

		plugin.call("run", 1_000L.toLittleEndian(), 1_002)
		assertEquals(0, plugin.remainingFuel())
		assertThrows<PluginException> { plugin.call("run", 1_000L.toLittleEndian(), 1_001) }
	}

	@Test
	fun `linear memory cannot grow past the cap`() {
		val plugin = ExtismPlugin(testPlugin("count"), instrumenter = FuelInstrumenter(maxMemoryPages = 16))

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

	private companion object {
		const val FUEL = 10_000_000L
	}
}
