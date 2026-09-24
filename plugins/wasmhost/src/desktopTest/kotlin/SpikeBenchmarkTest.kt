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
import kotlin.test.assertTrue
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
	fun `a novel through the Kotlin plugin`() {
		val wasm = pluginsRepo?.resolve("kotlin/upper/build/compileSync/wasmWasi/main/developmentExecutable/kotlin/upper.wasm")
		if (wasm?.exists() != true) return println("Kotlin plugin not built; skipped")

		lateinit var plugin: ExtismPlugin
		val load = measureTime { plugin = ExtismPlugin(wasm.readBytes()) }
		val input = novel.encodeToByteArray()
		val output = plugin.call("run", "warm up".encodeToByteArray(), FUEL)
		assertTrue(output.decodeToString() == "WARM UP")

		val time = measureTime { plugin.call("run", input, FUEL) }
		println("Kotlin upper: load $load, ${input.size} bytes in $time")
	}

	@Test
	fun `a novel through the C word frequency plugin`() {
		val wasm = pluginsRepo?.resolve("c/wordfreq/build/wordfreq.wasm")
		if (wasm?.exists() != true) return println("C plugin not built; skipped")

		lateinit var plugin: ExtismPlugin
		val load = measureTime { plugin = ExtismPlugin(wasm.readBytes()) }
		val chapters = novel.chunked(novel.length / SCENES).joinToString(",") { "\"${it.replace("\n", "\\n")}\"" }
		val request = """{"format":"wordfreq.csv","projectName":"Novel","language":"en","chapters":[{"name":"One","scenes":[$chapters]}]}"""
		val input = request.encodeToByteArray()
		plugin.call("export", input, FUEL)

		lateinit var csv: String
		val time = measureTime { csv = plugin.call("export", input, FUEL).decodeToString() }
		println("C wordfreq: load $load, ${input.size} bytes in $time")
		println(csv.lines().take(6).joinToString(" | "))
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
