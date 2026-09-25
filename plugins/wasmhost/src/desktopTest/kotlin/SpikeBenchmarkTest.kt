import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
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
	fun `upper-casing a novel, C against Kotlin`() {
		val input = novel.encodeToByteArray()
		compare("upper", input, "run", "c/upper/build/upper.wasm", "kotlin/upper/build/compileSync/wasmWasi/main/developmentExecutable/kotlin/upper.wasm")
	}

	@Test
	fun `a word count export of a novel, C against Kotlin`() {
		val scenes = novel.chunked(novel.length / SCENES).joinToString(",") { "\"${it.replace("\n", "\\n")}\"" }
		val request = """{"format":"x","projectName":"Novel","language":"en","chapters":[{"name":"One","scenes":[$scenes]}],""" +
			""""settings":{"perScene":true,"heading":"Word count"}}"""
		compare("word count", request.encodeToByteArray(), "export", "c/wordfreq/build/wordfreq.wasm", "kotlin/wordcount/build/package/plugin.wasm")
	}

	/** Loads each built plugin, warms it up, and prints its load time and best of three runs. */
	private fun compare(task: String, input: ByteArray, function: String, vararg builds: String) {
		builds.forEach { build ->
			val wasm = pluginsRepo?.resolve(build)?.takeIf { it.exists() } ?: return@forEach println("$build not built; skipped")
			lateinit var plugin: ExtismPlugin
			val load = measureTime { plugin = ExtismPlugin(wasm.readBytes(), listOf(noDispatch)) }
			plugin.call(function, input, FUEL)
			val best = (1..3).minOf { measureTime { plugin.call(function, input, FUEL) } }
			println("$task, $build (${wasm.length() / 1024} KB): load $load, run $best on ${input.size / 1024} KB")
		}
	}

	/** Plugins that call back into Hammer get an error reply, which they are written to tolerate. */
	private val noDispatch = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) {
		write("""{"error":{"kind":"NotFound","message":"benchmark"}}""".encodeToByteArray())
	}

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
