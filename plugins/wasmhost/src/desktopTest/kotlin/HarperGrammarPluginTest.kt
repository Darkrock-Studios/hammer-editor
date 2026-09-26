import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * The Harper grammar plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS
 * points at that checkout; prints how long loading and checking take.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class HarperGrammarPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private lateinit var registry: PluginRegistry

	private val check by lazy {
		val built = File(System.getenv("HAMMER_PLUGINS"), "rust/harper-grammar/build/harper-grammar.hammerplugin")
		check(built.exists()) { "Run rust/harper-grammar/build.sh in hammer-plugins first" }
		val download = "/downloads/harper-grammar.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath())
		plugins.install(download)
		registry = PluginRegistry().also(plugins::activate)
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { fileSystem }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
			)
		}
		registry.plugins.single().textDiagnostics().single()
	}

	/** Each issue in [paragraph] as the text it covers, then its fixes. */
	private fun issues(paragraph: String, language: String? = "en"): List<String> {
		val found = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf(paragraph), language, "Project")) }.single()
		return found.map { paragraph.substring(it.start, it.end) + " -> " + it.fixes.joinToString("|") { fix -> fix.replacement } }
	}

	/** The messages of the issues in [paragraph]. */
	private fun messages(paragraph: String): List<String> =
		runBlocking { check.diagnose(TextDiagnosticsRequest(listOf(paragraph), "en", "Project")) }.single().map { it.message }

	@Test
	fun `settings turn off long sentences and whole categories`() {
		val long = "When the storm finally broke over the valley late that evening, the old farmer and his two sons " +
			"hurried out across the muddy fields to gather the frightened sheep, calling to one another over the " +
			"wind while the rain soaked through their coats and the lanterns flickered."
		val agreement = "It were a quiet evening."
		assertTrue(messages(long).any { "words long" in it }, messages(long).toString())
		assertTrue(messages(agreement).isNotEmpty())

		val settings = registry.settings("harper-grammar")!!
		settings.set("longSentences", JsonPrimitive(false))
		settings.set("grammar", JsonPrimitive(false))

		assertTrue(messages(long).none { "words long" in it }, messages(long).toString())
		assertEquals(emptyList(), messages(agreement))
	}

	@Test
	fun `style issues are suggestions, and mistakes errors`() {
		val long = "When the storm finally broke over the valley late that evening, the old farmer and his two sons " +
			"hurried out across the muddy fields to gather the frightened sheep, calling to one another over the " +
			"wind while the rain soaked through their coats and the lanterns flickered like a ember."
		val found = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf(long), "en", "Project")) }.single()

		assertEquals(TextDiagnosticSeverity.Suggestion, found.single { "words long" in it.message }.severity)
		assertTrue(found.any { it.severity == TextDiagnosticSeverity.Error }, found.map { it.message }.toString())
	}

	@Test
	fun `the same words get one issue, with every rule's fixes`() {
		val paragraph = "He said that that the plan was fine."
		val found = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf(paragraph), "en", "Project")) }.single()
			.filter { paragraph.substring(it.start, it.end) == "that that" }

		assertEquals(1, found.size, found.map { it.message }.toString())
		val replacements = found.single().fixes.map { it.replacement }
		assertTrue("that" in replacements && "that which" in replacements, replacements.toString())
		assertEquals(replacements.distinct(), replacements)
	}

	@Test
	fun `a fix that inserts says what it inserts`() {
		val fixes = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf("However the plan worked."), "en", "Project")) }.single().flatMap { it.fixes }

		assertTrue(fixes.any { it.label == "Insert “,”" && it.replacement == "However," }, fixes.map { it.replacement to it.label }.toString())
	}

	@Test
	fun `grammar is checked in English and nothing else`() {
		val heap = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
		heap.forEach { it.resetPeakUsage() }
		val loaded = measureTimedValue { check }
		val first = measureTimedValue { issues("This is a apple, and the the store is closed.") }
		val peak = heap.sumOf { it.peakUsage.used } / (1024 * 1024)
		println("Install: ${loaded.duration}, first check: ${first.duration}, peak heap: $peak MB")
		assertTrue("a -> an" in first.value, first.value.toString())
		assertTrue("the the -> the" in first.value, first.value.toString())

		val paragraph = "It were a quiet evening, and nobody seem to notice her arrival."
		val one = measureTimedValue { issues(paragraph, "en-GB") }
		println("One paragraph: ${one.duration}")
		assertTrue(one.value.isNotEmpty())

		assertEquals(emptyList(), issues("Il a mangé une pomme.", "fr"))
	}
}
