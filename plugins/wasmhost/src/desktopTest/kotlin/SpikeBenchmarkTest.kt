import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import io.github.charlietap.chasm.embedding.dsl.imports
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.writeI64
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.random.Random
import kotlin.time.measureTime

/**
 * Timings for the runtime plugin spike. Runs only with HAMMER_WASM_BENCH=1; the plugins built in a
 * hammer-plugins checkout are used when HAMMER_PLUGINS points at it.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_WASM_BENCH", matches = "1")
class SpikeBenchmarkTest {

	private val novel: String = buildNovel()
	private val pluginsRepo = System.getenv("HAMMER_PLUGINS")?.let(::File)

	@Test
	fun `interpreter speed with and without fuel checks`() {
		val iterations = 20_000_000L
		val instrumented = ExtismPlugin(testPlugin("count"))
		instrumented.call("run", iterations.toLittleEndian(), Long.MAX_VALUE)
		val fuelled = measureTime { instrumented.call("run", iterations.toLittleEndian(), Long.MAX_VALUE) }

		val store = store()
		val raw = instance(
			store,
			module(testPlugin("count")).expect("decode"),
			imports(store) {
				function {
					moduleName = "extism:host/env"
					entityName = "input_load_u64"
					type {
						params { i64() }
						results { i64() }
					}
					reference(HostFunction { _, results -> results.writeI64(0, iterations) })
				}
			},
		).expect("instantiate")
		invoke(store, raw, "run")
		val bare = measureTime { invoke(store, raw, "run") }

		println("count to $iterations: bare $bare, with fuel $fuelled")
	}

	@Test
	fun `a novel through a WAT plugin a byte at a time`() {
		val plugin = ExtismPlugin(testPlugin("upper"))
		val input = novel.encodeToByteArray()
		plugin.call("run", "warm up".encodeToByteArray(), FUEL)

		val time = measureTime { plugin.call("run", input, FUEL) }
		println("upper.wat on ${input.size} bytes: $time")
	}

	@Test
	fun `upper-casing a novel`() {
		val input = novel.encodeToByteArray()
		compare(
			"upper", input, "run",
			"c/upper/build/upper.wasm",
			"assemblyscript/upper/build/upper.wasm",
			"rust/upper/build/upper.wasm",
			"go/upper/build/upper.wasm",
			"zig/upper/build/upper.wasm",
			"kotlin/upper/build/compileSync/wasmWasi/main/developmentExecutable/kotlin/upper.wasm",
			"kotlin/upper/build/compileSync/wasmWasi/main/productionExecutable/optimized/upper.wasm",
		)
	}

	@Test
	fun `a word count export of a novel`() {
		val scenes = novel.chunked(novel.length / SCENES).joinToString(",") { "\"${it.replace("\n", "\\n")}\"" }
		val request = """{"format":"x","projectName":"Novel","language":"en","chapters":[{"name":"One","scenes":[$scenes]}],""" +
			""""settings":{"perScene":true,"heading":"Word count"}}"""
		compare(
			"word count", request.encodeToByteArray(), "export",
			"c/wordfreq/build/wordfreq.wasm",
			"assemblyscript/wordfreq/build/wordfreq.wasm",
			"rust/wordfreq/build/wordfreq.wasm",
			"go/wordfreq/build/wordfreq.wasm",
			"zig/wordfreq/build/wordfreq.wasm",
			"kotlin/wordfreq/build/compileSync/wasmWasi/main/productionExecutable/optimized/wordfreq.wasm",
			"assemblyscript/wordcount/build/wordcount.wasm",
			"rust/wordcount/build/wordcount.wasm",
			"go/wordcount/build/wordcount.wasm",
			"zig/wordcount/build/wordcount.wasm",
			"kotlin/wordcount/build/compileSync/wasmWasi/main/developmentExecutable/kotlin/wordcount.wasm",
			"kotlin/wordcount/build/compileSync/wasmWasi/main/productionExecutable/optimized/wordcount.wasm",
		)
	}

	@Test
	fun `a style report on a novel, counted afresh`() {
		val scenes = novel.chunked(novel.length / SCENES)
		val nodes = scenes.indices.joinToString(",") { """{"id":${it + 1},"name":"Scene ${it + 1}","kind":"scene","children":[]}""" }
		val dispatch = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) { args ->
			val request = read(args[0]).decodeToString()
			val reply = if ("scene.tree" in request) {
				"""{"output":{"nodes":[$nodes]}}"""
			} else {
				val id = Regex("\"id\":(\\d+)").find(request)!!.groupValues[1].toInt()
				"""{"output":{"markdown":"${scenes[id - 1].replace("\n", "\\n")}"}}"""
			}
			write(reply.encodeToByteArray())
		}
		compare(
			"style", """{"action":"report","project":"Novel","settings":{}}""".encodeToByteArray(), "action",
			"c/style/build/style.wasm",
			"assemblyscript/style/build/style.wasm",
			"rust/style/build/style.wasm",
			"go/style/build/style.wasm",
			"zig/style/build/style.wasm",
			"kotlin/style/build/compileSync/wasmWasi/main/productionExecutable/optimized/style.wasm",
			userFunctions = listOf(dispatch, noCacheGet, noCacheSet),
			size = novel.length,
		)
	}

	/**
	 * Loads each built plugin, warms it up, and prints its load time and best of three runs, or how it
	 * failed.
	 */
	private fun compare(
		task: String,
		input: ByteArray,
		function: String,
		vararg builds: String,
		userFunctions: List<ExtismPlugin.UserFunction> = listOf(noDispatch, noCacheGet, noCacheSet),
		size: Int = input.size,
	) {
		builds.forEach { build ->
			val wasm = pluginsRepo?.resolve(build)?.takeIf { it.exists() } ?: return@forEach println("$build not built; skipped")
			lateinit var plugin: ExtismPlugin
			val load = measureTime { plugin = ExtismPlugin(wasm.readBytes(), userFunctions) }
			val best = try {
				plugin.call(function, input, FUEL)
				(1..3).minOf { measureTime { plugin.call(function, input, FUEL) } }
			} catch (e: PluginException) {
				return@forEach println("$task, $build failed: ${e.message}")
			}
			println("$task, $build (${wasm.length() / 1024} KB): load $load, run $best on ${size / 1024} KB")
		}
	}

	/** Plugins that call back into Hammer get an error reply, which they are written to tolerate. */
	private val noDispatch = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) {
		write("""{"error":{"kind":"NotFound","message":"benchmark"}}""".encodeToByteArray())
	}

	private val noCacheGet = ExtismPlugin.UserFunction("hammer_cache_get", params = 1, returnsValue = true) { 0 }
	private val noCacheSet = ExtismPlugin.UserFunction("hammer_cache_set", params = 2, returnsValue = false) { 0 }

	private fun buildNovel(): String {
		val words = listOf(
			"the", "storm", "came", "early", "that", "year", "and", "Alice", "ran", "for", "lighthouse",
			"keeper", "of", "salt", "wind", "harbor", "boats", "night", "she", "said", "nothing", "sea",
		)
		val random = Random(42)
		return buildString {
			repeat(NOVEL_WORDS) { i ->
				append(words[random.nextInt(words.size)])
				append(if (i % 17 == 16) ".\n\n" else " ")
			}
		}
	}

	private companion object {
		const val NOVEL_WORDS = 100_000
		const val SCENES = 60
		const val FUEL = 5_000_000_000L
	}
}
