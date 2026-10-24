import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.toJson
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The first meeting plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class FirstMeetingPluginTest {

	private val fileSystem = FakeFileSystem()
	private val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath())

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val action: PluginAction by lazy {
		// Alice and the White Rabbit share scenes 2 and 3; 9 is only Alice's.
		val names = mapOf(ALICE to "Alice", RABBIT to "White Rabbit")
		val referencedBy = mapOf(ALICE to listOf(2, 3, 9), RABBIT to listOf(2, 3))
		val read = operation<JsonObject, JsonObject>("entry.read", "", Access.Read, OperationScope.Content) { input ->
			val id = input["id"]!!.jsonPrimitive.int
			buildJsonObject {
				put("id", id)
				put("name", names.getValue(id))
				putJsonArray("referencedBy") { referencedBy.getValue(id).forEach { add(it) } }
			}
		}
		// Part One holds Chapter 1, which holds The Fall (3) and The Garden (9); Tea Party (2) comes after
		// it, so story order is not id order.
		val tree = operation<JsonObject, JsonObject>("scene.tree", "", Access.Read, OperationScope.Content) {
			buildJsonObject {
				putJsonArray("nodes") {
					add(node(10, "Part One", "group", node(11, "Chapter 1", "group", node(3, "The Fall"), node(9, "The Garden"))))
					add(node(2, "Tea Party"))
				}
			}
		}
		val built = File(PluginRepos.official, "first-meeting/build/first-meeting.hammerplugin")
		check(built.exists()) { "Run ./build.sh first-meeting in hammer-plugins first" }
		val download = "/downloads/first-meeting.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
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
				module { single { OperationRegistry(listOf(read, tree), NoProjects) } },
			)
		}
		registry.plugins.single().actions().single()
	}

	private fun node(id: Int, name: String, kind: String = "scene", vararg children: JsonElement) = buildJsonObject {
		put("id", id)
		put("name", name)
		put("kind", kind)
		put("wordCount", 0)
		putJsonArray("children") { children.forEach { add(it) } }
	}

	private fun run(place: ActionPlace, itemId: Int?, input: JsonObject = JsonObject(emptyMap()), button: String? = null, from: ActionPage? = null): ActionReply =
		runBlocking { action.run(ActionCall("Storm", place, itemId, input, button, from?.state)) }

	@Test
	fun `it is an action without fields from the project or an entry, reading entries and the scene tree`() {
		assertEquals("meet", action.name)
		assertEquals("Find where two characters first meet", action.label)
		assertEquals(setOf(ActionPlace.Project, ActionPlace.Entry), action.places)
		assertTrue(action.fields.isEmpty())
		assertFalse(action.keep)
		assertEquals(listOf("entry.read", "scene.tree"), plugins.installed().single().manifest?.permissions?.operations)
	}

	@Test
	fun `its first page picks two people, the first being the entry it was run from`() {
		val page = assertNotNull(run(ActionPlace.Entry, ALICE).page)
		val (a, b) = page.fields.map { assertIs<ActionField.Entity>(it) }
		assertEquals(listOf("a", "b"), listOf(a.key, b.key))
		listOf(a, b).forEach { field ->
			assertEquals(CatalogFilter(setOf(CatalogKind.Entry), setOf(EntryType.PERSON)), field.filter)
			assertEquals(true to false, field.required to field.multiple)
		}
		assertEquals(listOf(CatalogRef(CatalogKind.Entry, ALICE)), a.defaultRefs)
		assertTrue(b.defaultRefs.isEmpty())
		assertEquals(listOf("find"), page.buttons.map { it.id })
		assertTrue(page.buttons.single().primary)

		val fromProject = assertNotNull(run(ActionPlace.Project, null).page)
		assertTrue(fromProject.fields.all { (it as ActionField.Entity).defaultRefs.isEmpty() })
	}

	@Test
	fun `Find names the first scene the two share in story order, and its buttons open them`() {
		val input = JsonObject(mapOf("a" to CatalogRef(CatalogKind.Entry, ALICE).toJson(), "b" to CatalogRef(CatalogKind.Entry, RABBIT).toJson()))
		val found = assertNotNull(run(ActionPlace.Project, null, input, "find").page)
		assertEquals("Alice and White Rabbit", found.title)
		val markdown = assertNotNull(found.markdown)
		assertTrue(markdown.startsWith("**Alice** and **White Rabbit** first share a scene in **The Fall**, in Part One › Chapter 1."), markdown)
		assertTrue(markdown.endsWith("They share 2 scenes in all. The others, in story order:\n\n- **Tea Party**"), markdown)
		assertEquals(
			listOf(Triple("open:3", "The Fall", true), Triple("open:2", "Tea Party", true), Triple("back", "Back", false), Triple("open:3", "Open The Fall", false)),
			found.buttons.map { Triple(it.id, it.label, it.inline) },
		)
		assertEquals(false, found.buttons.single { it.id == "back" }.validate)
		assertTrue(found.buttons.last().primary)

		val open = run(ActionPlace.Project, null, JsonObject(emptyMap()), "open:2", found)
		assertEquals(ActionPlace.Scene, open.open?.place)
		assertEquals(2, open.open?.id)
		assertNull(open.page)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}

	private companion object {
		const val ALICE = 12
		const val RABBIT = 6
	}
}
