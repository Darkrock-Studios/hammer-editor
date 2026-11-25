import com.darkrockstudios.apps.hammer.plugins.wasmhost.CompiledStart
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ModuleCompiler
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import io.github.charlietap.chasm.embedding.instance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How ExtismPlugin uses a compiler, with the interpreter standing in for chasm-jit, which runs only on Android. */
class ModuleCompilerTest {

	private val logged = mutableListOf<String>()
	private val logging: (ExtismPlugin.LogLevel, String) -> Unit = { _, message -> logged += message }

	private val compiling = ModuleCompiler { store, module, imports -> CompiledStart.Started(instance(store, module, imports)) }

	@Test
	fun `a module the compiler starts runs as compiled`() {
		val plugin = ExtismPlugin(testPlugin("upper"), log = logging, compiler = compiling)

		assertTrue(plugin.compiled)
		assertEquals("CALL ME.", plugin.call("run", "Call me.".encodeToByteArray(), FUEL).decodeToString())
		assertEquals(emptyList(), logged)
	}

	@Test
	fun `a module the compiler refuses runs interpreted, with a warning`() {
		// Refused after allocating the module and running its start, as a DEX that ART will not load is.
		val refusing = ModuleCompiler { store, module, imports ->
			instance(store, module, imports)
			CompiledStart.Refused("JIT loading failed: test")
		}
		val plugin = ExtismPlugin(testPlugin("upper"), log = logging, compiler = refusing)

		assertFalse(plugin.compiled)
		assertEquals(listOf("Running interpreted, as its code could not be compiled: JIT loading failed: test"), logged)
		assertEquals("CALL ME.", plugin.call("run", "Call me.".encodeToByteArray(), FUEL).decodeToString())
	}

	@Test
	fun `a compiled module failing to start for its own reasons is not retried interpreted`() {
		val error = assertThrows<PluginException> { ExtismPlugin(testPlugin("start_spin"), log = logging, compiler = compiling) }

		assertEquals("Plugin ran out of fuel starting up", error.message)
		assertEquals(emptyList(), logged)
	}

	@Test
	fun `without a compiler a module is interpreted`() {
		assertFalse(ExtismPlugin(testPlugin("upper"), compiler = null).compiled)
	}

	private companion object {
		const val FUEL = 100_000_000L
	}
}
