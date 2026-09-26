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

/**
 * The name checker plugin from hammer-plugins, its report run by the host against a made-up
 * encyclopedia and story. Runs when HAMMER_PLUGINS points at that checkout.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class NameCheckerPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val entries = listOf(EntrySummary(1, "Hermione", EntryKind.Person), EntrySummary(2, "Kaelen", EntryKind.Person))

	private val scenes = mapOf(
		10 to ("La lettre" to "Hermione lut la lettre. Hermoine soupira. Brennick rit, puis Brennick partit."),
		11 to ("Chapitre deux" to "Hermione salua Brennick."),
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
			operation<ProjectInput, Info>("project.info", "", Access.Read, OperationScope.Content) { Info("fr-FR") },
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
		plugin.actions().single().run(ActionCall("Tempête", ActionPlace.Project, null, JsonObject(emptyMap()))).markdown!!
	}

	@Test
	fun `the report lists mentions, likely misspellings, and names with no entry`() {
		assertEquals(
			"""
			# Names

			## Encyclopedia entries

			- **Hermione**: 2 mentions in 2 scenes, first in *La lettre*
			- Not mentioned: **Kaelen**

			## Possible misspellings

			- **Hermoine**: 1 time, in *La lettre*. Close to Hermione.

			## Names with no entry

			- **Brennick**: 3 times, in 2 scenes
			""".trimIndent(),
			report(load(locale = null)),
		)
	}

	@Test
	fun `in French, its labels come from its translation and its report from the locale`() {
		val french = load(locale = "fr-FR")
		assertEquals("Rapport sur les noms", french.actions().single().label)
		assertEquals("Vérificateur de noms", french.textDiagnostics().single().label)
		assertEquals(
			"""
			# Noms

			## Entrées de l’encyclopédie

			- **Hermione** : 2 mentions dans 2 scènes, d’abord dans *La lettre*
			- Sans mention : **Kaelen**

			## Fautes possibles

			- **Hermoine** : 1 fois, dans *La lettre*. Ressemble à Hermione.

			## Noms sans entrée

			- **Brennick** : 3 fois, dans 2 scènes
			""".trimIndent(),
			// French's no-break spaces before colons, as plain ones.
			report(french).replace('\u00A0', ' '),
		)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
