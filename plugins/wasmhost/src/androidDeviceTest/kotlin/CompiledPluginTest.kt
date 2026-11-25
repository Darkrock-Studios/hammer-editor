import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** The host's limits hold in code chasm-jit compiles, and how much faster that code runs. */
@RunWith(AndroidJUnit4::class)
class CompiledPluginTest {

	@Test
	fun compiledCodeTransformsItsInput() {
		val plugin = ExtismPlugin(testPlugin("upper"))

		assertTrue(plugin.compiled)
		assertEquals("CALL ME ISHMAEL.", plugin.call("run", "Call me Ishmael.".encodeToByteArray(), FUEL).decodeToString())
	}

	@Test
	fun compiledCodeCallsHostFunctions() {
		val reverse = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) { args ->
			write(read(args[0]).reversedArray())
		}
		val plugin = ExtismPlugin(testPlugin("dispatch_echo"), listOf(reverse))

		assertTrue(plugin.compiled)
		assertEquals("cba", plugin.call("run", "abc".encodeToByteArray(), FUEL).decodeToString())
	}

	@Test
	fun compiledCodeRunsOutOfFuel() {
		val plugin = ExtismPlugin(testPlugin("count"))

		assertTrue(plugin.compiled)
		plugin.call("run", 1_000L.toLittleEndian(), 1_002)
		assertEquals(0, plugin.remainingFuel())
		assertFailsWith<PluginException> { plugin.call("run", 1_000L.toLittleEndian(), 1_001) }
	}

	@Test
	fun compiledStartFunctionRunsOutOfFuel() {
		val error = assertFailsWith<PluginException> { ExtismPlugin(testPlugin("start_spin"), log = logging) }
		assertEquals("Plugin ran out of fuel starting up", error.message)
		assertEquals(emptyList(), logged)
	}

	@Test
	fun anotherThreadCanInterruptCompiledCode() {
		val plugin = ExtismPlugin(testPlugin("spin"))
		var error: PluginException? = null
		val call = thread { error = runCatching { plugin.call("run", ByteArray(0), Long.MAX_VALUE) }.exceptionOrNull() as? PluginException }

		while (!plugin.interrupt()) Thread.sleep(1)
		call.join(5_000)
		assertTrue(plugin.compiled)
		assertEquals("Plugin was interrupted in run", error?.message)
		assertFalse(plugin.interrupt())
	}

	@Test
	fun compiledRecursionRunsOutOfStack() {
		val plugin = ExtismPlugin(testPlugin("recurse"), maxStackBytes = 64L * 1024)

		assertTrue(plugin.compiled)
		val error = assertFailsWith<PluginException> { plugin.call("run", ByteArray(0), 10_000_000) }
		assertEquals("Plugin ran out of stack in run", error.message)
	}

	@Test
	fun compiledRecursionRunsOutOfStackAtTheDefaultCap() {
		val plugin = ExtismPlugin(testPlugin("recurse"))

		assertTrue(plugin.compiled)
		val error = assertFailsWith<PluginException> { plugin.call("run", ByteArray(0), Long.MAX_VALUE) }
		assertEquals("Plugin ran out of stack in run", error.message)
	}

	@Test
	fun compiledGcObjectsCannotGrowPastTheCap() {
		val plugin = ExtismPlugin(testPlugin("gc_heap"), maxGuestHeapBytes = 4L * 1024 * 1024)

		assertTrue(plugin.compiled)
		val error = assertFailsWith<PluginException> { plugin.call("hoard", ByteArray(0), FUEL) }
		assertEquals("Plugin ran out of memory in hoard", error.message)
	}

	@Test
	fun compiledGcGarbageIsFreedWhenACallReturns() {
		val plugin = ExtismPlugin(testPlugin("gc_heap"), maxGuestHeapBytes = 16L * 1024 * 1024)

		assertTrue(plugin.compiled)
		repeat(50) { plugin.call("churn", ByteArray(0), FUEL) }
	}

	@Test
	fun compiledLinearMemoryCannotGrowPastTheCap() {
		val plugin = ExtismPlugin(testPlugin("count"), maxMemoryPages = 16)

		assertTrue(plugin.compiled)
		val error = assertFailsWith<PluginException> { plugin.call("grow", ByteArray(0), FUEL) }
		assertTrue("code -1" in error.message!!, error.message)
	}

	@Test
	fun compiledTablesCannotGrowPastTheCap() {
		val plugin = ExtismPlugin(testPlugin("table"))

		assertTrue(plugin.compiled)
		val error = assertFailsWith<PluginException> { plugin.call("grow", ByteArray(0), FUEL) }
		assertTrue("code -1" in error.message!!, error.message)
	}

	@Test
	fun compiledModuleWhoseMemoryStartsPastTheCapDoesNotStart() {
		val error = assertFailsWith<PluginException> { ExtismPlugin(testPlugin("big_memory"), log = logging) }

		assertEquals("Plugin needs more memory than its limit of 64 MiB", error.message)
		assertEquals(emptyList(), logged)
		assertTrue(ExtismPlugin(testPlugin("big_memory"), maxMemoryPages = 1100).compiled)
	}

	@Test
	fun compiledModuleWhoseTableStartsPastTheCapDoesNotStart() {
		val error = assertFailsWith<PluginException> { ExtismPlugin(testPlugin("big_table"), log = logging) }

		assertEquals("Plugin needs a table larger than its limit of 100000 elements", error.message)
		assertEquals(emptyList(), logged)
		assertTrue(ExtismPlugin(testPlugin("big_table"), maxTableElements = 100_001).compiled)
	}

	@Test
	fun aModuleTheJitRefusesRunsInterpreted() {
		val plugin = ExtismPlugin(testPlugin("jit_refused"), log = logging)

		assertFalse(plugin.compiled)
		assertTrue(logged.single().startsWith("Running interpreted, as its code could not be compiled: JIT "), logged.single())
		plugin.call("run", ByteArray(0), FUEL)
	}

	@Test
	fun codeFailingToLoadInACallFailsThePluginNotTheHost() {
		// ART reports a generated class it cannot load as a LinkageError when a call first reaches it.
		val unloadable = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) {
			throw NoClassDefFoundError("chunk")
		}
		val plugin = ExtismPlugin(testPlugin("dispatch_echo"), listOf(unloadable))

		val error = assertFailsWith<PluginException> { plugin.call("run", "abc".encodeToByteArray(), FUEL) }
		assertTrue(error.message!!.startsWith("Plugin's compiled code failed to load in run"), error.message)
		assertTrue(plugin.compiledCodeFailed)
		assertFailsWith<PluginException> { plugin.call("run", "abc".encodeToByteArray(), FUEL) }
	}

	@Test
	fun cancellingStopsCompiling() {
		val cancellation = Job().apply { cancel() }

		assertFailsWith<CancellationException> { ExtismPlugin(testPlugin("upper"), compileCancellation = cancellation) }
	}

	@Test
	fun interpretedWhenAsked() {
		assertFalse(ExtismPlugin(testPlugin("upper"), compile = false).compiled)
	}

	@Test
	fun countingSpeed() {
		val iterations = 20_000_000L
		fun time(compile: Boolean): kotlin.time.Duration {
			val plugin = ExtismPlugin(testPlugin("count"), compile = compile)
			plugin.call("run", 1_000L.toLittleEndian(), Long.MAX_VALUE)
			return measureTime { plugin.call("run", iterations.toLittleEndian(), Long.MAX_VALUE) }
		}
		val interpreted = time(compile = false)
		val compiled = time(compile = true)
		Log.i(TAG, "count to $iterations: interpreted $interpreted, compiled $compiled")
	}

	@Test
	fun upperCasingSpeed() {
		val input = buildString { while (length < 1_000_000) append("It was the best of times, it was the worst of times. ") }.encodeToByteArray()
		fun time(compile: Boolean): kotlin.time.Duration {
			val plugin = ExtismPlugin(testPlugin("upper"), compile = compile)
			plugin.call("run", "warm up".encodeToByteArray(), FUEL)
			return measureTime { plugin.call("run", input, FUEL) }
		}
		val interpreted = time(compile = false)
		val compiled = time(compile = true)
		Log.i(TAG, "upper on ${input.size} bytes: interpreted $interpreted, compiled $compiled")
	}

	// What a plugin logs from the host, which is where a fallback to the interpreter is reported.
	private val logged = mutableListOf<String>()
	private val logging: (ExtismPlugin.LogLevel, String) -> Unit = { _, message -> logged += message }

	private fun testPlugin(name: String): ByteArray =
		requireNotNull(javaClass.getResourceAsStream("/plugins/$name.wasm")) { "No test plugin $name" }.use { it.readBytes() }

	private fun Long.toLittleEndian(): ByteArray = ByteArray(8) { (this ushr (8 * it)).toByte() }

	private companion object {
		const val TAG = "CompiledPluginTest"
		const val FUEL = 100_000_000L
	}
}
