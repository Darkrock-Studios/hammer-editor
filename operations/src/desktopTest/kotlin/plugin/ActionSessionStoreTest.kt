package plugin

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityIdChange
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
import com.darkrockstudios.apps.hammer.operations.plugin.EntityRenumbering
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import com.darkrockstudios.apps.hammer.operations.plugin.RenumberingFollower
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.UnreadableSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.toJson
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
		ActionField.Entity(
			"who",
			"Who",
			filter = CatalogFilter(setOf(CatalogKind.Entry), setOf(EntryType.PERSON), setOf("pov")),
			required = true,
			defaultRefs = listOf(CatalogRef(CatalogKind.Entry, 12)),
		),
		ActionField.Entity("where", "Where", multiple = true),
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

	private val storm = ProjectDef(project, "Storm", HPath("/projects/Storm", "Storm", false))

	private fun picks(vararg refs: CatalogRef) = JsonArray(refs.map { it.toJson() })

	private suspend fun ActionSessionStore.renumber(vararg changes: EntityIdChange) = onEntityIdsChanged(storm, changes.toList())

	@Test
	fun `a run from an item sync renumbered is kept under its new id, picks and all`() = runTest {
		val fromEntry = key.copy(place = ActionPlace.Entry, itemId = 12)
		val values = JsonObject(
			mapOf(
				"who" to CatalogRef(CatalogKind.Entry, 12).toJson(),
				"where" to picks(CatalogRef(CatalogKind.Note, 12), CatalogRef(CatalogKind.Entry, 12), CatalogRef(CatalogKind.Entry, 5)),
				"more" to JsonArray(listOf(JsonPrimitive(12), JsonPrimitive(3))),
				"age" to JsonPrimitive(12),
			)
		)
		store.save(fromEntry, ActionSession("1.2.0", page, values, first = false, answers = emptyMap()))

		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		assertNull(store.load(fromEntry))
		val moved = assertNotNull(store.load(fromEntry.copy(itemId = 40)))
		assertEquals(false, moved.renumbered)
		assertEquals(CatalogRef(CatalogKind.Entry, 40).toJson(), moved.values["who"])
		// Only entries are renumbered: a note and a scene with the same number are other items.
		assertEquals(picks(CatalogRef(CatalogKind.Note, 12), CatalogRef(CatalogKind.Entry, 40), CatalogRef(CatalogKind.Entry, 5)), moved.values["where"])
		assertEquals(values["more"], moved.values["more"])
		assertEquals(JsonPrimitive(12), moved.values["age"])
		val who = assertIs<ActionField.Entity>(assertNotNull(moved.page).fields.single { it.key == "who" })
		assertEquals(listOf(CatalogRef(CatalogKind.Entry, 40)), who.defaultRefs)
		assertEquals(listOf("interview.entry.40.json"), fileSystem.list(sessionDir).map { it.name })
	}

	@Test
	fun `a sync's changes are followed in order, in one pass`() = runTest {
		val values = JsonObject(mapOf("more" to JsonArray(listOf(JsonPrimitive(12), JsonPrimitive(3))), "scenes" to JsonPrimitive(3)))
		store.save(key, ActionSession("1.2.0", page, values, first = false, answers = emptyMap()))

		store.renumber(EntityIdChange(EntityType.Scene, 3, 41), EntityIdChange(EntityType.Scene, 12, 42))

		val loaded = assertNotNull(store.load(key))
		assertEquals(JsonArray(listOf(JsonPrimitive(42), JsonPrimitive(41))), loaded.values["more"])
		assertEquals(JsonPrimitive(41), loaded.values["scenes"])
		assertEquals(listOf(41), assertIs<ActionField.Scenes>(assertNotNull(loaded.page).fields.single { it.key == "scenes" }).defaultIds)
	}

	@Test
	fun `a run whose state may hold the old id is marked, and one whose numbers are something else is not`() = runTest {
		val marked = mapOf(
			"number" to JsonPrimitive(12),
			"button" to JsonPrimitive("open:12"),
			"whole" to JsonPrimitive("12"),
			"picked" to CatalogRef(CatalogKind.Entry, 12).toJson(),
		)
		val unmarked = mapOf(
			"longer" to JsonPrimitive(120),
			"label" to JsonPrimitive("open:112"),
			"prose" to JsonPrimitive("She was 12 when it began."),
			"word" to JsonPrimitive("q12"),
			"otherKind" to CatalogRef(CatalogKind.Note, 12).toJson(),
			"keyed" to JsonObject(mapOf("proust/12" to JsonPrimitive("Courage"))),
		)
		for ((name, state) in marked + unmarked) {
			store.save(key.copy(action = name), session(page = ActionPage(fields = fields, state = JsonObject(mapOf("s" to state)))))
		}

		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		marked.keys.forEach { assertEquals(true, store.load(key.copy(action = it))?.renumbered, it) }
		unmarked.keys.forEach { assertEquals(false, store.load(key.copy(action = it))?.renumbered, it) }
	}

	@Test
	fun `other projects and runs from other kinds of item are left as they are`() = runTest {
		val other = ProjectId("00000000-0000-4000-8000-000000000009")
		val elsewhere = key.copy(projectId = other, place = ActionPlace.Entry, itemId = 12)
		val fromNote = key.copy(place = ActionPlace.Note, itemId = 12)
		store.save(elsewhere, session())
		store.save(fromNote, session(page = ActionPage(markdown = "Nothing picked here.")))
		val before = fileSystem.read(sessionDir / "interview.note.12.json") { readUtf8() }

		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		assertNotNull(store.load(elsewhere))
		assertEquals(before, fileSystem.read(sessionDir / "interview.note.12.json") { readUtf8() })
	}

	@Test
	fun `a session left under the new id is set aside, not written over`() = runTest {
		val fromEntry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(fromEntry, session())
		store.save(fromEntry.copy(itemId = 40), session(version = "0.1.0"))

		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		assertEquals("1.2.0", store.load(fromEntry.copy(itemId = 40))?.pluginVersion)
		assertTrue(fileSystem.exists(sessionDir / "interview.entry.40.json.superseded"))
	}

	@Test
	fun `a session that cannot be followed leaves no copy under the old id`() = runTest {
		val fromEntry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(fromEntry, session())
		val failing = object : ForwardingFileSystem(fileSystem) {
			override fun sink(file: Path, mustCreate: Boolean) = throw IOException("disk full")
		}

		ActionSessionStore(failing, pluginsDir).renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		assertEquals(listOf("interview.entry.40.json"), fileSystem.list(sessionDir).map { it.name })
	}

	@Test
	fun `a plugin's sessions that cannot be listed leave the others followed, and followers told`() = runTest {
		val fromEntry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(fromEntry, session())
		store.save(fromEntry.copy(pluginId = "broken"), session())
		val failing = object : ForwardingFileSystem(fileSystem) {
			override fun listOrNull(dir: Path) = if (dir.parent?.name == "broken") throw IOException("unreadable") else super.listOrNull(dir)
		}
		val failingStore = ActionSessionStore(failing, pluginsDir)
		val told = mutableListOf<List<EntityRenumbering>>()
		failingStore.followRenumberings(object : RenumberingFollower {
			override fun renumbered(renumberings: List<EntityRenumbering>) {
				told += renumberings
			}
		})

		failingStore.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))

		assertNotNull(store.load(fromEntry.copy(itemId = 40)))
		assertEquals(listOf(listOf(EntityRenumbering(project, EntityType.EncyclopediaEntry, 12, 40))), told)
	}

	@Test
	fun `followers hold their writes through the pass, then are told, until they stop`() = runTest {
		val fromEntry = key.copy(place = ActionPlace.Entry, itemId = 12)
		store.save(fromEntry, session())
		val events = mutableListOf<String>()
		val unfollow = store.followRenumberings(object : RenumberingFollower {
			override fun hold(renumberings: List<EntityRenumbering>, pass: () -> Unit) {
				events += "hold"
				pass()
				events += if (store.load(fromEntry.copy(itemId = 40)) != null) "followed" else "not followed"
			}

			override fun renumbered(renumberings: List<EntityRenumbering>) {
				events += "told ${renumberings.single().newId}"
			}
		})

		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 12, 40))
		unfollow()
		store.renumber(EntityIdChange(EntityType.EncyclopediaEntry, 13, 41))

		assertEquals(listOf("hold", "followed", "told 40"), events)
	}

	private fun FieldTable.describe() =
		listOf(key, type, label, default, hint, min, max, multiline, options.map { it.value to it.label }, multiple, required, format, kinds, entryTypes, tags)
}
