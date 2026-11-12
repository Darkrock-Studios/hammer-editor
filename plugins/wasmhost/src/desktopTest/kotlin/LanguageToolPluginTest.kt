import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.server.HammerServiceClient
import com.darkrockstudios.apps.hammer.common.server.ServiceReply
import com.darkrockstudios.apps.hammer.common.server.ServiceRequest
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
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
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals

/**
 * The LanguageTool plugin from hammer-plugins with "Your Hammer server" chosen, run by the host against
 * a fake sync server. Runs when HAMMER_PLUGINS points at that checkout.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class LanguageToolPluginTest {

	private val fileSystem = FakeFileSystem()
	private val sent = mutableListOf<ServiceRequest>()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun registry(reply: ServiceReply): PluginRegistry {
		val built = File(PluginRepos.official, "languagetool/build/languagetool.hammerplugin")
		check(built.exists()) { "Run ./build.sh languagetool in hammer-plugins first" }
		val download = "/downloads/languagetool.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath(), null)
		plugins.install(download)
		val registry = PluginRegistry().also(plugins::activate)
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { fileSystem }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
					single<HammerServiceClient> { HammerServiceClient { request -> sent += request; reply } }
				},
				registry.koinModule(),
			)
		}
		registry.settings("languagetool")!!.set("service", JsonPrimitive("hammer"))
		return registry
	}

	private fun check(registry: PluginRegistry, paragraph: String) = runBlocking {
		registry.plugins.single().textDiagnostics().single()
			.diagnose(TextDiagnosticsRequest(listOf(paragraph), "en-US", "Project"))
	}.single()

	@Test
	fun `a check goes to the sync server's LanguageTool and its matches come back`() {
		val matches = """{"matches":[{"message":"Use 'is'","replacements":[{"value":"is"}],"offset":5,"length":3,""" +
			""""rule":{"id":"AGREEMENT","issueType":"grammar","category":{"id":"GRAMMAR"}}}]}"""
		val registry = registry(ServiceReply.Response(200, matches.encodeToByteArray()))

		val found = check(registry, "This are wrong.")

		val request = sent.single()
		assertEquals("languagetool" to "/v2/check", request.service to request.path)
		assertEquals("text=This+are+wrong.&language=en-US", request.body!!.decodeToString())
		assertEquals(listOf("are" to listOf("is")), found.map { "This are wrong.".substring(it.start, it.end) to it.fixes.map { fix -> fix.replacement } })
	}
}
