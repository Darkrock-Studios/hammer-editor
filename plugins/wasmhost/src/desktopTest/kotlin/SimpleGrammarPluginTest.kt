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

/** The simple grammar plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class SimpleGrammarPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val check by lazy {
		val built = File(System.getenv("HAMMER_PLUGINS"), "c/simple-grammar/build/simple-grammar.hammerplugin")
		check(built.exists()) { "Run c/build.sh simple-grammar in hammer-plugins first" }
		val download = "/downloads/simple-grammar.hammerplugin".toPath()
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
		return found.map { paragraph.substring(it.start, it.end) + " -> " + it.fixes.joinToString("|") { fix -> fix.replacement } }
	}

	@Test
	fun `repeated words, except those English repeats`() {
		assertEquals(listOf("the The -> the"), issues("Over the The hill."))
		assertEquals(emptyList(), issues("She had had enough, and said that that was that."))
	}

	@Test
	fun `a and an where the sound is plain`() {
		assertEquals(listOf("a -> an", "An -> A"), issues("It was a apple. An dog barked."))
		assertEquals(emptyList(), issues("A one-off, a unicorn, an hour, a European, an FBI agent, an x-ray."))
	}

	@Test
	fun `mistaken phrases keep their capital`() {
		assertEquals(listOf("Could of -> Could have", "alot -> a lot"), issues("Could of been worse, alot worse."))
	}

	@Test
	fun `a lower-case I`() {
		assertEquals(listOf("i -> I", "i -> I"), issues("Then i left, and i’m glad. That is, i.e. gone."))
	}

	@Test
	fun `spacing around punctuation`() {
		assertEquals(
			listOf("   ->  ", " , -> ,", ", -> , ", ". -> . "),
			issues("Too  far , and red,green.Then more.  Two spaces after a sentence are fine."),
		)
	}

	@Test
	fun `a sentence starting in lower case, not after an abbreviation or ellipsis`() {
		assertEquals(listOf("a -> A", "t -> T"), issues("She left. a dog barked! then quiet… e.g. this, etc. and... that."))
	}

	@Test
	fun `offsets count UTF-16 units past accented letters`() {
		assertEquals(listOf("the the -> the"), issues("Café the the end."))
	}

	@Test
	fun `other languages get nothing`() {
		assertEquals(emptyList(), issues("Il a a dit.", "fr"))
		assertEquals(listOf("a a -> a"), issues("It a a dog.", "en-GB"))
		assertEquals(listOf("a a -> a"), issues("It a a dog.", null))
	}
}
