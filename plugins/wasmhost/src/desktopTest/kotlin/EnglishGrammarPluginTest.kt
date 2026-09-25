import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * The English grammar plugin, on Harper, from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS
 * points at that checkout; prints how long loading and checking take.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class EnglishGrammarPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val check by lazy {
		val built = File(System.getenv("HAMMER_PLUGINS"), "rust/english-grammar/build/english-grammar.hammerplugin")
		check(built.exists()) { "Run rust/english-grammar/build.sh in hammer-plugins first" }
		val download = "/downloads/english-grammar.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath())
		plugins.install(download)
		val registry = PluginRegistry().also(plugins::activate)
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
		val found = runBlocking { check.diagnose(listOf(paragraph), language) }.single()
		return found.map { paragraph.substring(it.start, it.end) + " -> " + it.fixes.joinToString("|") }
	}

	@Test
	fun `grammar is checked in English and nothing else`() {
		val loaded = measureTimedValue { check }
		val first = measureTimedValue { issues("This is a apple, and the the store is closed.") }
		println("Install: ${loaded.duration}, first check: ${first.duration}")
		assertTrue("a -> an" in first.value, first.value.toString())
		assertTrue("the the -> the" in first.value, first.value.toString())

		val paragraph = "It were a quiet evening, and nobody seem to notice her arrival."
		val one = measureTimedValue { issues(paragraph, "en-GB") }
		println("One paragraph: ${one.duration}")
		assertTrue(one.value.isNotEmpty())

		assertEquals(emptyList(), issues("Il a mangé une pomme.", "fr"))
	}
}
