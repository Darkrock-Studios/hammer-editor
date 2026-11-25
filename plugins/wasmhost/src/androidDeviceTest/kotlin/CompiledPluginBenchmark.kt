import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.DEFAULT_MAX_MEMORY_PAGES
import com.darkrockstudios.apps.hammer.plugins.wasmhost.ExtismPlugin
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PAGES_PER_MIB
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.platformCompiler
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Interpreted against compiled timings for the test fixtures and for real plugin builds, pushed to [ROOT]
 * by `adb push`. Runs only with the instrumentation argument `bench=1`.
 */
@RunWith(AndroidJUnit4::class)
class CompiledPluginBenchmark {

	private val novel by lazy { buildNovel() }

	@Before
	fun enabled() {
		assumeTrue(InstrumentationRegistry.getArguments().getString("bench") == "1")
	}

	@Test
	fun wordCountExportOfANovel() {
		val scenes = novel.chunked(novel.length / SCENES).joinToString(",") { "\"${it.replace("\n", "\\n")}\"" }
		val request = """{"format":"x","projectName":"Novel","language":"en","chapters":[{"name":"One","scenes":[$scenes]}],""" +
			""""settings":{"perScene":true,"heading":"Word count"}}"""
		compare("wordcount", request.encodeToByteArray(), "export", stubs)
	}

	@Test
	fun styleReportOnANovel() {
		// Kotlin/Wasm's report on a whole novel needs more than a 512 MB heap gives it.
		val story = buildNovel(STYLE_WORDS)
		val scenes = story.chunked(story.length / SCENES)
		val nodes = scenes.indices.joinToString(",") { """{"id":${it + 1},"name":"Scene ${it + 1}","kind":"scene","children":[]}""" }
		val dispatch = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) { args ->
			val request = read(args[0]).decodeToString()
			val reply = if ("scene.tree" in request) {
				"""{"output":{"nodes":[$nodes]}}"""
			} else if ("project.info" in request) {
				"""{"output":{"language":"en"}}"""
			} else {
				val id = Regex("\"id\":(\\d+)").find(request)!!.groupValues[1].toInt()
				"""{"output":{"markdown":"${scenes[id - 1].replace("\n", "\\n")}"}}"""
			}
			write(reply.encodeToByteArray())
		}
		compare(
			"style", """{"action":"report","project":"Novel","settings":{}}""".encodeToByteArray(), "action",
			listOf(dispatch, noCacheGet, noCacheSet, noResource, noProgress),
		)
	}

	@Test
	fun grammarCheckOfAChapter() {
		compare(
			"grammar", grammarRequest(), "diagnose", stubs, maxMemoryPages = HARPER_MEMORY_PAGES,
		)
	}

	/**
	 * One Harper build, from the `module` argument, in the mode the `compile` argument names, so each runs
	 * in a process of its own: load time, the first check, which builds the linter, and each check after it.
	 */
	@Test
	fun harper() {
		val path = InstrumentationRegistry.getArguments().getString("module")
		assumeTrue("No module argument", path != null)
		val compile = InstrumentationRegistry.getArguments().getString("compile") == "1"
		val request = grammarRequest()
		val wasm = File(path!!).readBytes()
		lateinit var plugin: ExtismPlugin
		val load = measureTime {
			plugin = ExtismPlugin(wasm, stubs, maxMemoryPages = HARPER_MEMORY_PAGES, compiler = platformCompiler.takeIf { compile })
		}
		// ART compiles the generated DEX itself as it warms up, so each call is timed to show where it settles.
		lateinit var output: ByteArray
		val calls = List(HARPER_CALLS) { measureTime { output = plugin.call("diagnose", request, FUEL) } }
		val mode = if (plugin.compiled) "compiled" else "interpreted"
		Log.i(TAG, "harper ${File(path).name} (${wasm.size / 1024} KB) $mode: load ${load.ms()}, calls ${calls.joinToString { it.ms() }} on $PARAGRAPHS paragraphs, output ${output.size} bytes, hash ${output.contentHashCode()}")
	}

	@Test
	fun countingSpeed() {
		val iterations = 20_000_000L
		val (interpreted, compiled) = listOf(false, true).map { compile ->
			val plugin = ExtismPlugin(testPlugin("count"), compiler = platformCompiler.takeIf { compile })
			plugin.call("run", 1_000L.toLittleEndian(), FUEL)
			measureTime { plugin.call("run", iterations.toLittleEndian(), FUEL) }
		}
		Log.i(TAG, "count to $iterations: interpreted $interpreted, compiled $compiled")
	}

	@Test
	fun upperCasingSpeed() {
		val input = buildString { while (length < 1_000_000) append("It was the best of times, it was the worst of times. ") }.encodeToByteArray()
		val (interpreted, compiled) = listOf(false, true).map { compile ->
			val plugin = ExtismPlugin(testPlugin("upper"), compiler = platformCompiler.takeIf { compile })
			plugin.call("run", "warm up".encodeToByteArray(), FUEL)
			measureTime { plugin.call("run", input, FUEL) }
		}
		Log.i(TAG, "upper on ${input.size} bytes: interpreted $interpreted, compiled $compiled")
	}

	private fun grammarRequest(): ByteArray {
		val paragraphs = novel.split("\n\n").take(PARAGRAPHS).joinToString(",") { "\"${it.trim()}\"" }
		return """{"diagnostics":"grammar","paragraphs":[$paragraphs],"language":"en","project":"Novel","settings":{},"locale":null}"""
			.encodeToByteArray()
	}

	/** Each build in [ROOT]/[task], loaded and run interpreted then compiled: load time and best of three runs. */
	private fun compare(
		task: String,
		input: ByteArray,
		function: String,
		userFunctions: List<ExtismPlugin.UserFunction>,
		maxMemoryPages: Int = DEFAULT_MAX_MEMORY_PAGES,
	) {
		val builds = File(ROOT, task).listFiles { file -> file.extension == "wasm" }.orEmpty().sortedBy { it.length() }
		assumeTrue("Nothing in $ROOT/$task", builds.isNotEmpty())
		builds.forEach { build ->
			val wasm = build.readBytes()
			listOf(false, true).forEach { compile ->
				val mode = if (compile) "compiled" else "interpreted"
				try {
					lateinit var plugin: ExtismPlugin
					val load = measureTime { plugin = ExtismPlugin(wasm, userFunctions, maxMemoryPages = maxMemoryPages, compiler = platformCompiler.takeIf { compile }) }
					plugin.call(function, input, FUEL)
					val best = (1..3).minOf { measureTime { plugin.call(function, input, FUEL) } }
					val ran = if (plugin.compiled == compile) mode else "interpreted (could not compile)"
					Log.i(TAG, "$task ${build.name} (${wasm.size / 1024} KB) $ran: load ${load.ms()}, run ${best.ms()}")
				} catch (e: PluginException) {
					Log.i(TAG, "$task ${build.name} $mode failed: ${e.message}")
				} catch (e: OutOfMemoryError) {
					Log.i(TAG, "$task ${build.name} $mode ran the host out of memory")
				}
				System.gc()
			}
		}
	}

	private fun Duration.ms() = "${inWholeMilliseconds} ms"

	private val noDispatch = ExtismPlugin.UserFunction("hammer_dispatch", params = 1, returnsValue = true) {
		write("""{"error":{"kind":"NotFound","message":"benchmark"}}""".encodeToByteArray())
	}
	private val noResource = ExtismPlugin.UserFunction("hammer_resource", params = 1, returnsValue = true) { 0 }
	private val noProgress = ExtismPlugin.UserFunction("hammer_progress", params = 1, returnsValue = false) { 0 }
	private val noCacheGet = ExtismPlugin.UserFunction("hammer_cache_get", params = 1, returnsValue = true) { 0 }
	private val noCacheSet = ExtismPlugin.UserFunction("hammer_cache_set", params = 2, returnsValue = false) { 0 }

	// A module asks only for the ones it imports.
	private val stubs = listOf(noDispatch, noCacheGet, noCacheSet, noResource, noProgress)

	private fun buildNovel(wordCount: Int = NOVEL_WORDS): String {
		val words = listOf(
			"the", "storm", "came", "early", "that", "year", "and", "Alice", "ran", "for", "lighthouse",
			"keeper", "of", "salt", "wind", "harbor", "boats", "night", "she", "said", "nothing", "sea",
		)
		val random = Random(42)
		return buildString {
			repeat(wordCount) { i ->
				append(words[random.nextInt(words.size)])
				append(if (i % 17 == 16) ".\n\n" else " ")
			}
		}
	}

	private companion object {
		const val TAG = "CompiledPluginBenchmark"
		const val ROOT = "/data/local/tmp/hammer-bench"
		const val NOVEL_WORDS = 100_000
		const val STYLE_WORDS = 30_000
		const val SCENES = 60
		const val PARAGRAPHS = 300
		const val HARPER_CALLS = 12
		const val FUEL = Long.MAX_VALUE

		// Harper's manifest asks for 256 MiB of linear memory.
		const val HARPER_MEMORY_PAGES = 256 * PAGES_PER_MIB
	}
}
