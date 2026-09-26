import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The name generator plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class NameGeneratorPluginTest {

	private val fileSystem = FakeFileSystem()
	private val created = mutableListOf<JsonObject>()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val action: PluginAction by lazy {
		val create = operation<JsonObject, JsonObject>("entry.create", "", Access.Write, OperationScope.Content) { input ->
			created += input
			JsonObject(emptyMap())
		}
		val built = File(System.getenv("HAMMER_PLUGINS"), "c/name-generator/build/name-generator.hammerplugin")
		check(built.exists()) { "Run c/build.sh name-generator in hammer-plugins first" }
		val download = "/downloads/name-generator.hammerplugin".toPath()
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
				module { single { OperationRegistry(listOf(create), NoProjects) } },
			)
		}
		registry.plugins.single().actions().single()
	}

	private val input = JsonObject(
		mapOf(
			"style" to JsonPrimitive("norse"),
			"gender" to JsonPrimitive("feminine"),
			"count" to JsonPrimitive(3),
			"surnames" to JsonPrimitive(true),
		)
	)

	@Test
	fun `it asks for a style, gender, count, and surnames, from the project and entries`() {
		assertEquals(listOf("style", "gender", "count", "surnames"), action.fields.map { it.key })
		assertEquals(setOf(ActionPlace.Project, ActionPlace.Entry), action.places)
		assertTrue(action.fields.all { it is ActionField.Setting })
	}

	@Test
	fun `a name's button adds it as a person, and the same list comes back with it marked`() {
		val first = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, input)) }
		val names = first.markdown!!.lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") }
		assertEquals(3, names.size)
		assertTrue(names.all { it.endsWith("sdottir") }, names.toString())
		assertEquals(names + "More names", first.buttons.map { it.label })
		assertEquals(listOf(false, false, false, true), first.buttons.map { it.footer })

		val add = first.buttons.first()
		val second = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, input, button = add.id)) }

		assertEquals(names.first(), created.single()["name"]!!.jsonPrimitive.content)
		assertEquals("person", created.single()["type"]!!.jsonPrimitive.content)
		assertEquals("Added ${names.first()} to the encyclopedia", second.message)
		assertTrue("- ${names.first()} (added)" in second.markdown!!, second.markdown)
		assertEquals(names.drop(1) + "More names", second.buttons.map { it.label })
	}

	@Test
	fun `every style it offers has names in its package`() {
		val styles = assertIs<SettingDeclaration.Choice>((action.fields.first { it.key == "style" } as ActionField.Setting).declaration)
		styles.options.forEach { style ->
			val styled = JsonObject(input + ("style" to JsonPrimitive(style.value)))
			val reply = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, styled)) }
			assertEquals(3, reply.markdown!!.lines().count { it.startsWith("- ") }, style.value)
		}
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
