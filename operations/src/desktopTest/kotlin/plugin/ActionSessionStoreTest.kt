package plugin

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.operations.plugin.ActionAnswer
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSession
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.UnreadableSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.toFieldTable
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionSessionStoreTest {

	private val fileSystem = FakeFileSystem()
	private val pluginsDir = "/config/plugins".toPath()
	private val store = ActionSessionStore(fileSystem, pluginsDir)

	private val project = ProjectId("00000000-0000-4000-8000-000000000001")
	private val key = ActionSessionKey("people", project, "interview", ActionPlace.Project, null)
	private val sessionDir = pluginsDir / "_sessions" / "people" / project.id

	private val fields = listOf(
		ActionField.Text("name", "Name", hint = "Who", required = true),
		ActionField.Text("story", "Story", format = TextFormat.RichExtended, defaultValue = "It *began*."),
		ActionField.Text("notes", "Notes", multiline = true),
		ActionField.Scenes("scenes", "Scenes", multiple = false, required = true, defaultIds = listOf(3)),
		ActionField.Scenes("more", "More scenes"),
		ActionField.Setting(SettingDeclaration.Toggle("brave", "Brave", true, hint = "Is she?")),
		ActionField.Setting(SettingDeclaration.Number("age", "Age", 30, min = 1, max = 120)),
		ActionField.Setting(
			SettingDeclaration.Choice("set", "Set", "full", listOf("quick", "full").map { SettingDeclaration.Choice.Option(it, it.uppercase()) })
		),
	)

	private val page = ActionPage(
		title = "Mara: Background (2 of 8)",
		markdown = "Tell me about **Mara**.",
		fields = fields,
		buttons = listOf(ActionButton("back", "Back", validate = false), ActionButton("next", "Next", primary = true, inline = true)),
		state = JsonObject(mapOf("step" to JsonPrimitive(2), "answers" to JsonArray(listOf(JsonPrimitive("a"))))),
	)

	private fun session(page: ActionPage? = this.page, version: String? = "1.2.0") = ActionSession(
		pluginVersion = version,
		page = page,
		values = JsonObject(mapOf("name" to JsonPrimitive("Mara"), "age" to JsonPrimitive(31))),
		first = false,
		answers = mapOf(
			"name" to ActionAnswer("Name", JsonPrimitive("Mara")),
			"brave" to ActionAnswer("Brave", JsonPrimitive(false)),
		),
	)

	@Test
	fun `a session round trips, page, values, and answers`() {
		store.save(key, session())

		val loaded = assertNotNull(store.load(key))
		assertEquals("1.2.0", loaded.pluginVersion)
		assertEquals(false, loaded.first)
		assertEquals(session().values, loaded.values)
		assertEquals(listOf("name", "brave"), loaded.answers.keys.toList())
		assertEquals(JsonPrimitive(false), loaded.answers["brave"]?.value)
		assertEquals("Name", loaded.answers["name"]?.label)

		val restored = assertNotNull(loaded.page)
		assertEquals(page.title, restored.title)
		assertEquals(page.markdown, restored.markdown)
		assertEquals(page.state, restored.state)
		assertEquals(page.fields.map { it.toFieldTable().describe() }, restored.fields.map { it.toFieldTable().describe() })
		assertEquals(
			page.buttons.map { listOf(it.id, it.label, it.primary, it.inline, it.validate) },
			restored.buttons.map { listOf(it.id, it.label, it.primary, it.inline, it.validate) },
		)
	}

	@Test
	fun `nothing saved loads as none`() {
		assertNull(store.load(key))
	}

	@Test
	fun `a save is staged and moved into place, leaving no staging file`() {
		store.save(key, session())
		store.save(key, session(version = "1.3.0"))

		assertEquals(listOf(sessionDir / "interview.project.json"), fileSystem.list(sessionDir))
		assertEquals("1.3.0", store.load(key)?.pluginVersion)
	}

	@Test
	fun `each project, place, and item has its own file`() {
		val other = ProjectId("00000000-0000-4000-8000-000000000002")
		store.save(key, session())
		store.save(key.copy(place = ActionPlace.Entry, itemId = 12), session())
		store.save(key.copy(place = ActionPlace.Scene, itemId = 3), session())
		store.save(key.copy(projectId = other), session())

		assertEquals(
			listOf("interview.entry.12.json", "interview.project.json", "interview.scene.3.json"),
			fileSystem.list(sessionDir).map { it.name }.sorted(),
		)
		assertTrue(fileSystem.exists(pluginsDir / "_sessions" / "people" / other.id / "interview.project.json"))
	}

	@Test
	fun `remove clears only its own session`() {
		val entry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(key, session())
		store.save(entry, session())

		store.remove(key)
		store.remove(key)

		assertNull(store.load(key))
		assertNotNull(store.load(entry))
	}

	@Test
	fun `a session over 4 MiB is refused and the last one kept`() {
		store.save(key, session())
		val huge = ActionPage(fields = fields, state = JsonPrimitive("x".repeat(ActionSessionStore.MAX_SESSION_BYTES)))

		assertFailsWith<ActionSessionException> { store.save(key, session(page = huge)) }
		assertEquals(page.title, store.load(key)?.page?.title)
		assertEquals(listOf(sessionDir / "interview.project.json"), fileSystem.list(sessionDir))
	}

	@Test
	fun `a corrupt file is moved aside, so a new run cannot write over it`() {
		val file = sessionDir / "interview.project.json"
		fileSystem.createDirectories(sessionDir)
		fileSystem.write(file) { writeUtf8("{\"format\": 1, \"pluginVer") }

		val failure = assertFailsWith<UnreadableSessionException> { store.load(key) }
		val aside = sessionDir / "interview.project.json.unreadable"
		assertEquals(aside, failure.setAside)
		assertEquals("{\"format\": 1, \"pluginVer", fileSystem.read(aside) { readUtf8() })
		assertNull(store.load(key))

		store.save(key, session())
		assertNotNull(store.load(key))
		assertTrue(fileSystem.exists(aside))
	}

	@Test
	fun `a file in another format is moved aside, beside any set aside before`() {
		val file = sessionDir / "interview.project.json"
		repeat(2) {
			store.save(key, session())
			val newer = fileSystem.read(file) { readUtf8() }.replace("\"format\":1", "\"format\":2")
			fileSystem.write(file) { writeUtf8(newer) }
			assertFailsWith<UnreadableSessionException> { store.load(key) }
		}

		assertEquals(
			listOf("interview.project.json.unreadable", "interview.project.json.unreadable-2"),
			fileSystem.list(sessionDir).map { it.name }.sorted(),
		)
	}

	@Test
	fun `an unreadable file that cannot be moved aside is left in place`() {
		val file = sessionDir / "interview.project.json"
		fileSystem.createDirectories(sessionDir)
		fileSystem.write(file) { writeUtf8("not json") }
		val failing = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) = throw IOException("read-only")
		}

		val failure = assertFailsWith<UnreadableSessionException> { ActionSessionStore(failing, pluginsDir).load(key) }
		assertNull(failure.setAside)
		assertEquals("not json", fileSystem.read(file) { readUtf8() })
	}

	@Test
	fun `a page that can no longer be read still gives the answers`() {
		store.save(key, session())
		val file = sessionDir / "interview.project.json"
		val unknown = fileSystem.read(file) { readUtf8() }.replace("\"type\":\"bool\"", "\"type\":\"slider\"")
		fileSystem.write(file) { writeUtf8(unknown) }

		val loaded = assertNotNull(store.load(key))
		assertNull(loaded.page)
		assertEquals(listOf("name", "brave"), loaded.answers.keys.toList())
	}

	@Test
	fun `a failed write is thrown and leaves no staging file`() {
		val failing = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) = throw IOException("disk full")
		}
		val store = ActionSessionStore(failing, pluginsDir)

		val failure = assertFailsWith<ActionSessionException> { store.save(key, session()) }
		assertIs<IOException>(failure.cause)
		assertEquals(emptyList(), fileSystem.list(sessionDir))
	}

	@Test
	fun `a name that is not safe as a file is never written or read`() {
		val unsafe = key.copy(action = "../escape")

		assertFailsWith<ActionSessionException> { store.save(unsafe, session()) }
		assertNull(store.load(unsafe))
		assertTrue(!fileSystem.exists(pluginsDir / "_sessions"))
	}

	@Test
	fun `a project given a new id keeps its sessions, and the old id keeps them too`() = runTest {
		val newId = ProjectId("00000000-0000-4000-8000-000000000003")
		val moved = key.copy(projectId = newId)
		val entry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(key, session())
		store.save(entry, session())
		store.save(moved.copy(place = ActionPlace.Entry, itemId = 12), session(version = "9.0.0"))
		store.save(ActionSessionKey("other", project, "ask", ActionPlace.Project, null), session())

		store.onProjectIdChanged(ProjectDef(newId, "Storm", HPath("/projects/Storm", "Storm", false)), project, newId)

		assertEquals("1.2.0", store.load(moved)?.pluginVersion)
		assertEquals("9.0.0", store.load(moved.copy(place = ActionPlace.Entry, itemId = 12))?.pluginVersion)
		assertNotNull(store.load(ActionSessionKey("other", newId, "ask", ActionPlace.Project, null)))
		assertNotNull(store.load(key))
		assertNotNull(store.load(entry))
		assertEquals(
			listOf("interview.entry.12.json", "interview.project.json"),
			fileSystem.list(pluginsDir / "_sessions" / "people" / newId.id).map { it.name }.sorted(),
		)
	}

	private fun FieldTable.describe() =
		listOf(key, type, label, default, hint, min, max, multiline, options.map { it.value to it.label }, multiple, required, format)
}
