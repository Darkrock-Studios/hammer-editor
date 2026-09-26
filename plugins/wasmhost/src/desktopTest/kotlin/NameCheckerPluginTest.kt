import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.core.Entries
import com.darkrockstudios.apps.hammer.operations.core.EntryKind
import com.darkrockstudios.apps.hammer.operations.core.EntryListInput
import com.darkrockstudios.apps.hammer.operations.core.EntrySummary
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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

/**
 * The name checker plugin from hammer-plugins, run by the host against a made-up encyclopedia and
 * story. Runs when HAMMER_PLUGINS points at that checkout.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class NameCheckerPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val entries = listOf(
		EntrySummary(1, "Hermione Granger", EntryKind.Person),
		EntrySummary(2, "Kaelen", EntryKind.Person, aliases = listOf("Kae")),
		EntrySummary(3, "Grace", EntryKind.Person),
	)

	private val scenes = mapOf(
		10 to ("The Letter" to "# Morning\n\nHermione Granger read it. Hermoine sighed. Brennick laughed, and later Brennick left."),
		11 to ("Chapter Two" to "Hermione waved at Brennick. The Grangers waved back."),
	)

	@Serializable
	class Info(val language: String?)

	@Serializable
	class Text(val markdown: String)

	private fun load(locale: String?): ClientPlugin {
		val built = File(System.getenv("HAMMER_PLUGINS"), "rust/name-checker/build/name-checker.hammerplugin")
		check(built.exists()) { "Run rust/build.sh in hammer-plugins first" }
		val download = "/downloads/name-checker.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath(), locale)
		plugins.install(download)
		val registry = PluginRegistry().also(plugins::activate)
		val operations = listOf(
			operation<EntryListInput, Entries>("entry.list", "", Access.Read, OperationScope.Content) { Entries(entries) },
			operation<ProjectInput, Info>("project.info", "", Access.Read, OperationScope.Content) { Info("en-GB") },
			operation<ProjectInput, SceneTree>("scene.tree", "", Access.Read, OperationScope.Content) {
				SceneTree(scenes.map { (id, scene) -> SceneNode(id, scene.first, SceneKind.Scene, 0, emptyList()) })
			},
			operation<ProjectItemInput, Text>("scene.read", "", Access.Read, OperationScope.Content) { Text(scenes.getValue(it.id).second) },
		)
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { fileSystem }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
				// After the registry's module, which declares the app's own operations.
				module { single { OperationRegistry(operations, NoProjects) } },
			)
		}
		return registry.plugins.single()
	}

	private fun report(plugin: ClientPlugin): String = runBlocking {
		plugin.actions().single().run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))).markdown!!
	}

	/** Each issue in [paragraph] as the text it covers, then its fixes. */
	private fun issues(plugin: ClientPlugin, paragraph: String, language: String? = "en"): List<String> {
		val check = plugin.textDiagnostics().single()
		val found = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf(paragraph), language, "Storm")) }.single()
		return found.map { paragraph.substring(it.start, it.end) + " -> " + it.fixes.joinToString("|") { fix -> fix.replacement } }
	}

	@Test
	fun `a slip of a name, or a name in lower case, is underlined with the name as its fix`() {
		assertEquals(
			listOf("Hermoine -> Hermione", "kaelen -> Kaelen"),
			issues(load(locale = null), "Hermoine’s letter reached Grade, Grace, and kaelen, but not Hermione, Kae, or the Grangers."),
		)
	}

	@Test
	fun `names are checked in each language it has a word list for`() {
		val plugin = load(locale = null)
		assertEquals(listOf("Hermoine -> Hermione"), issues(plugin, "Hermoine sourit.", language = "fr"))
		assertEquals(emptyList(), issues(plugin, "Hermoine sorriu.", language = "pt"))
	}

	@Test
	fun `the report finds mentions, slips, and names with no entry`() {
		val report = report(load(locale = null))
		assertTrue("- **Hermione Granger**: 3 mentions in 2 scenes, first in *The Letter*" in report, report)
		assertTrue("- Not mentioned: **Grace**, **Kaelen**" in report, report)
		assertTrue("- **Hermoine**: 1 time, in *The Letter*. Close to Hermione." in report, report)
		assertTrue("- **Brennick**: 3 times, in 2 scenes" in report, report)
		assertTrue("Grangers" !in report, report)
	}

	@Test
	fun `in French, its labels come from its translation and its words from the locale`() {
		val french = load(locale = "fr-FR")
		assertEquals("Rapport sur les noms", french.actions().single().label)
		assertEquals("Vérificateur de noms", french.textDiagnostics().single().label)

		val found = runBlocking { french.textDiagnostics().single().diagnose(TextDiagnosticsRequest(listOf("Hermoine left."), "en", "Storm")) }
		assertEquals("«\u00A0Hermoine\u00A0» est proche de «\u00A0Hermione\u00A0» dans l’encyclopédie", found.single().single().message)

		// French's no-break spaces before colons, as plain ones.
		val report = report(french).replace('\u00A0', ' ')
		assertTrue(report.startsWith("# Noms\n\n## Entrées de l’encyclopédie"), report)
		assertTrue("- **Hermione Granger** : 3 mentions dans 2 scènes, d’abord dans *The Letter*" in report, report)
		assertTrue("- Sans mention : **Grace**, **Kaelen**" in report, report)
		assertTrue("- **Hermoine** : 1 fois, dans *The Letter*. Ressemble à Hermione." in report, report)
		assertTrue("- **Brennick** : 3 fois, dans 2 scènes" in report, report)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
