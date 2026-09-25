package operations

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.dependencyinjection.RAW_FILESYSTEM
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.Activity
import com.darkrockstudios.apps.hammer.operations.core.ArchivedScenes
import com.darkrockstudios.apps.hammer.operations.core.DateRangeInput
import com.darkrockstudios.apps.hammer.operations.core.DraftListInput
import com.darkrockstudios.apps.hammer.operations.core.DraftText
import com.darkrockstudios.apps.hammer.operations.core.Drafts
import com.darkrockstudios.apps.hammer.operations.core.Entries
import com.darkrockstudios.apps.hammer.operations.core.Entry
import com.darkrockstudios.apps.hammer.operations.core.EntryImage
import com.darkrockstudios.apps.hammer.operations.core.EntryKind
import com.darkrockstudios.apps.hammer.operations.core.EntityKind
import com.darkrockstudios.apps.hammer.operations.core.EntryListInput
import com.darkrockstudios.apps.hammer.operations.core.ExportFormats
import com.darkrockstudios.apps.hammer.operations.core.ExportedFile
import com.darkrockstudios.apps.hammer.operations.core.IdeaListInput
import com.darkrockstudios.apps.hammer.operations.core.Ideas
import com.darkrockstudios.apps.hammer.operations.core.Note
import com.darkrockstudios.apps.hammer.operations.core.NoteListInput
import com.darkrockstudios.apps.hammer.operations.core.Notes
import com.darkrockstudios.apps.hammer.operations.core.OperationList
import com.darkrockstudios.apps.hammer.operations.core.ProjectExportInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectInfo
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectList
import com.darkrockstudios.apps.hammer.operations.core.ProjectStats
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneMeta
import com.darkrockstudios.apps.hammer.operations.core.SceneText
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.core.SearchFilter
import com.darkrockstudios.apps.hammer.operations.core.SearchInput
import com.darkrockstudios.apps.hammer.operations.core.SearchResults
import com.darkrockstudios.apps.hammer.operations.core.Sessions
import com.darkrockstudios.apps.hammer.operations.core.StatsProjectInput
import com.darkrockstudios.apps.hammer.operations.core.TagFindInput
import com.darkrockstudios.apps.hammer.operations.core.TagListInput
import com.darkrockstudios.apps.hammer.operations.core.TaggedEntities
import com.darkrockstudios.apps.hammer.operations.core.TaggedEntity
import com.darkrockstudios.apps.hammer.operations.core.Tags
import com.darkrockstudios.apps.hammer.operations.core.Timeline
import com.darkrockstudios.apps.hammer.operations.core.TimelineEntry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Runs the read operations against the app's real Koin graph over a fake filesystem. */
class ReadOperationsTest : KoinComponent {

	private val ffs = FakeFileSystem()

	// FakeFileSystem is not thread-safe, so the test and every dispatcher share one thread.
	private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
	private lateinit var ids: Seeded

	private class Seeded(
		val chapter: SceneItem,
		val opening: SceneItem,
		val storm: SceneItem,
		val prologue: SceneItem,
		val cut: SceneItem,
		val draftId: Int,
		val entryId: Int,
	)

	@BeforeEach
	fun setUp() {
		ffs.createDirectories(getDefaultRootDocumentDirectory().toPath())
		val strRes = mockk<StrRes>(relaxed = true)
		val overrides = module {
			single<FileSystem> { ffs }
			single(named(RAW_FILESYSTEM)) { ffs } bind FileSystem::class
			single { strRes }
			single<CoroutineContext>(named(DISPATCHER_MAIN)) { thread }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { thread }
			single<CoroutineContext>(named(DISPATCHER_IO)) { thread }
		}
		GlobalContext.startKoin {
			allowOverride(true)
			modules(listOf(mainModule, overrides) + PluginRegistry(emptyList()).koinModules())
		}
		ids = onTestThread { seed() }
	}

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
		thread.close()
	}

	private fun <T> onTestThread(block: suspend () -> T): T = runBlocking(thread) { block() }

	private suspend fun seed(): Seeded {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		val def = (created as ClientResult.Success<ProjectDef>).data
		lateinit var seeded: Seeded
		temporaryProjectTask(def) { scope ->
			val scenes = scope.get<SceneEditorService>()
			val prologue = scenes.createScene(null, "Prologue")!!
			scenes.storeSceneMarkdownRaw(SceneContent(prologue, markdown = "Before it all."))
			val chapter = scenes.createGroup(null, "Chapter One")!!
			val opening = scenes.createScene(chapter, "Opening")!!
			scenes.storeSceneMarkdownRaw(SceneContent(opening, markdown = "The storm came *early* that year."))
			scenes.storeMetadata(SceneMetadata(outline = "Set up the storm", tags = setOf("weather")), opening.id)
			val storm = scenes.createScene(chapter, "Landfall")!!
			scenes.storeSceneMarkdownRaw(SceneContent(storm, markdown = "Alice ran for the lighthouse."))
			val cut = scenes.createScene(chapter, "Cut")!!
			scenes.storeSceneMarkdownRaw(SceneContent(cut, markdown = "Nobody needs this."))
			scenes.archiveScene(cut)

			val draft = scope.get<SceneDraftRepository>().saveDraft(opening, "First pass")!!

			val notes = scope.get<NotesRepository>()
			notes.createNote("Check the tide tables", setOf("research"))
			notes.createNote("Rename the boat")

			val encyclopedia = scope.get<EncyclopediaRepository>()
			val alice = encyclopedia.createEntry("Alice", EntryType.PERSON, "The keeper.", setOf("cast"), null)
				.instance!!.entry
			encyclopedia.createEntry("Lighthouse", EntryType.PLACE, "On the point.", setOf("weather"), null)
			val aliceDef = alice.toDef(def)
			ffs.write(encyclopedia.getEntryImagePath(aliceDef, "png").toOkioPath()) { write(IMAGE) }

			val timeline = scope.get<TimeLineRepository>()
			timeline.createEvent("The storm makes landfall", "Day 2", tags = setOf("weather"))
			timeline.createEvent("Alice arrives", "Day 1")

			scope.get<WritingActivityDatasource>().saveDeviceLog(
				"device-a",
				DeviceLog(
					deviceLabel = "Laptop",
					sessions = listOf(
						session(DAY_ONE, 120),
						session(DAY_ONE, 30),
						session(DAY_TWO, 200),
						session(DAY_TWO, 0),
					),
				),
			)

			seeded = Seeded(chapter, opening, storm, prologue, cut, draft.id, alice.id)
		}

		val ideas = get<IdeasRepository>()
		ideas.createIdea("A second lighthouse", "Sequel")
		val archived = (ideas.createIdea("A talking gull") as ClientResult.Success).data
		ideas.archiveIdea(archived.id)

		return seeded
	}

	private fun session(day: LocalDate, words: Int) = day.atStartOfDayIn(TimeZone.currentSystemDefault())
		.plus(9.hours)
		.let { WritingSession(startedAt = it, endedAt = it.plus(30.minutes), wordsWritten = words) }

	private suspend fun <I, O> run(op: String, input: I): O {
		val registry = get<OperationRegistry>()
		@Suppress("UNCHECKED_CAST")
		return registry.run(registry.find(op) as Operation<I, O>, input)
	}

	@Test
	fun `project list and info describe the project`() = onTestThread {
		val list = run<NoInput, ProjectList>("project.list", NoInput)
		assertEquals(listOf(PROJECT), list.projects.map { it.name })
		assertNull(list.projects.single().serverProjectId)

		val info = run<ProjectInput, ProjectInfo>("project.info", ProjectInput(PROJECT))
		assertEquals(PROJECT, info.name)
	}

	@Test
	fun `unknown projects are not found`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<ProjectInput, SceneTree>("scene.tree", ProjectInput("Missing"))
		}
		assertEquals(OperationException.Kind.NotFound, error.kind)
	}

	@Test
	fun `scene tree nests groups and counts words`() = onTestThread {
		val tree = run<ProjectInput, SceneTree>("scene.tree", ProjectInput(PROJECT))

		assertEquals(listOf("Prologue", "Chapter One"), tree.nodes.map { it.name })
		val chapter = tree.nodes[1]
		assertEquals(SceneKind.Group, chapter.kind)
		assertEquals(listOf("Opening", "Landfall"), chapter.children.map { it.name })
		assertEquals(6, chapter.children[0].wordCount)
		assertEquals(11, chapter.wordCount)
	}

	@Test
	fun `scene read returns the text and metadata`() = onTestThread {
		val scene = run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, ids.opening.id))

		assertEquals("The storm came *early* that year.", scene.markdown)
		assertEquals("Set up the storm", scene.meta.outline)
		assertEquals(listOf("weather"), scene.meta.tags)
		assertFalse(scene.archived)
	}

	@Test
	fun `archived scenes are listed and readable`() = onTestThread {
		val archived = run<ProjectInput, ArchivedScenes>("scene.archived", ProjectInput(PROJECT))
		assertEquals(listOf(ids.cut.id), archived.scenes.map { it.id })

		val scene = run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, ids.cut.id))
		assertTrue(scene.archived)
		assertEquals("Nobody needs this.", scene.markdown)
	}

	@Test
	fun `groups are not scenes`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, ids.chapter.id))
		}
		assertEquals(OperationException.Kind.NotFound, error.kind)
	}

	@Test
	fun `scene meta read`() = onTestThread {
		val meta = run<ProjectItemInput, SceneMeta>("scene.meta.read", ProjectItemInput(PROJECT, ids.opening.id))
		assertEquals("Set up the storm", meta.outline)
	}

	@Test
	fun `drafts are listed and read`() = onTestThread {
		val drafts = run<DraftListInput, Drafts>("draft.list", DraftListInput(PROJECT, ids.opening.id))
		assertEquals(listOf("First pass"), drafts.drafts.map { it.name })

		val draft = run<ProjectItemInput, DraftText>("draft.read", ProjectItemInput(PROJECT, ids.draftId))
		assertEquals(ids.opening.id, draft.sceneId)
		assertEquals("The storm came *early* that year.", draft.markdown)
	}

	@Test
	fun `notes filter by tag`() = onTestThread {
		val all = run<NoteListInput, Notes>("note.list", NoteListInput(PROJECT))
		assertEquals(2, all.notes.size)

		val tagged = run<NoteListInput, Notes>("note.list", NoteListInput(PROJECT, tag = "#research"))
		assertEquals(listOf("Check the tide tables"), tagged.notes.map { it.content })

		val note = run<ProjectItemInput, Note>("note.read", ProjectItemInput(PROJECT, tagged.notes.single().id))
		assertEquals(listOf("research"), note.tags)
	}

	@Test
	fun `entries filter by type and tag`() = onTestThread {
		val all = run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT))
		assertEquals(listOf("Alice", "Lighthouse"), all.entries.map { it.name })

		val places = run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT, type = EntryKind.Place))
		assertEquals(listOf("Lighthouse"), places.entries.map { it.name })

		val cast = run<EntryListInput, Entries>("entry.list", EntryListInput(PROJECT, tag = "cast"))
		assertEquals(listOf("Alice"), cast.entries.map { it.name })
	}

	@Test
	fun `entry read and image`() = onTestThread {
		val entry = run<ProjectItemInput, Entry>("entry.read", ProjectItemInput(PROJECT, ids.entryId))
		assertEquals("The keeper.", entry.text)
		assertEquals(EntryKind.Person, entry.type)
		assertTrue(entry.hasImage)

		val image = run<ProjectItemInput, EntryImage>("entry.image.get", ProjectItemInput(PROJECT, ids.entryId))
		assertEquals("png", image.extension)
		assertContentEquals(IMAGE, image.content)
	}

	@Test
	fun `timeline is in order`() = onTestThread {
		val timeline = run<ProjectInput, Timeline>("timeline.list", ProjectInput(PROJECT))
		assertEquals(listOf("The storm makes landfall", "Alice arrives"), timeline.events.map { it.content })

		val event = run<ProjectItemInput, TimelineEntry>(
			"timeline.read",
			ProjectItemInput(PROJECT, timeline.events.first().id),
		)
		assertEquals("Day 2", event.date)
	}

	@Test
	fun `ideas filter by archived`() = onTestThread {
		assertEquals(2, run<IdeaListInput, Ideas>("idea.list", IdeaListInput()).ideas.size)
		val active = run<IdeaListInput, Ideas>("idea.list", IdeaListInput(archived = false))
		assertEquals(listOf("Sequel"), active.ideas.map { it.title })
		val archived = run<IdeaListInput, Ideas>("idea.list", IdeaListInput(archived = true))
		assertEquals(listOf("A talking gull"), archived.ideas.map { it.content })
	}

	@Test
	fun `search finds every kind`() = onTestThread {
		val all = run<SearchInput, SearchResults>("search", SearchInput(PROJECT, "storm"))
		assertEquals(
			setOf(EntityKind.Scene, EntityKind.Timeline),
			all.results.map { it.kind }.toSet(),
		)

		val notes = run<SearchInput, SearchResults>("search", SearchInput(PROJECT, "tide", SearchFilter.Notes))
		assertEquals(listOf(EntityKind.Note), notes.results.map { it.kind })
	}

	@Test
	fun `tags are ranked and found`() = onTestThread {
		val tags = run<TagListInput, Tags>("tag.list", TagListInput(PROJECT))
		assertEquals("weather", tags.tags.first().tag)
		assertEquals(3, tags.tags.first().count)

		val sceneTags = run<TagListInput, Tags>("tag.list", TagListInput(PROJECT, kind = EntityKind.Scene))
		assertEquals(listOf("weather"), sceneTags.tags.map { it.tag })

		val found = run<TagFindInput, TaggedEntities>("tag.find", TagFindInput(PROJECT, "#weather"))
		assertEquals(
			setOf(EntityKind.Scene, EntityKind.Entry, EntityKind.Timeline),
			found.entities.map(TaggedEntity::kind).toSet(),
		)
	}

	@Test
	fun `export renders the chosen format`() = onTestThread {
		val formats = run<NoInput, ExportFormats>("export.formats", NoInput)
		assertTrue(formats.formats.any { it.id == "markdown" })

		val file = run<ProjectExportInput, ExportedFile>(
			"project.export",
			ProjectExportInput(PROJECT, format = "markdown", sceneIds = listOf(ids.storm.id)),
		)
		val text = file.content.decodeToString()
		assertTrue("Alice ran for the lighthouse." in text)
		assertFalse("Before it all." in text)
		assertEquals("$PROJECT.md", file.fileName)
	}

	@Test
	fun `export rejects unknown formats`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<ProjectExportInput, ExportedFile>("project.export", ProjectExportInput(PROJECT, format = "doc"))
		}
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	@Test
	fun `project stats`() = onTestThread {
		val stats = run<StatsProjectInput, ProjectStats>("stats.project", StatsProjectInput(PROJECT, recalculate = true))
		assertEquals(14, stats.totalWords)
		assertEquals(listOf("Prologue", "Chapter One"), stats.chapters.map { it.name })
		assertEquals(2, stats.noteCount)
	}

	@Test
	fun `activity totals days in range`() = onTestThread {
		val activity = run<DateRangeInput, Activity>("stats.activity", DateRangeInput(PROJECT, from = DAY_TWO))
		assertEquals(listOf(DAY_TWO), activity.days.map { it.date })
		assertEquals(200, activity.days.single().words)
		assertEquals(350, activity.devices.single().words)

		val sessions = run<DateRangeInput, Sessions>("stats.sessions", DateRangeInput(PROJECT, to = DAY_ONE))
		assertEquals(listOf(120, 30), sessions.sessions.map { it.words })
	}

	@Test
	fun `dates out of order are invalid`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<DateRangeInput, Activity>("stats.activity", DateRangeInput(PROJECT, from = DAY_TWO, to = DAY_ONE))
		}
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	@Test
	fun `reading a project that is not open leaves its unsaved edits alone`() = onTestThread {
		val def = get<ProjectsRepository>().findProject(PROJECT)!!
		val scenes = SceneDatasource(def, ffs)
		val recovered = SceneContent(ids.prologue, markdown = "Recovered edit.")
		scenes.storeTempSceneBuffer(SceneBuffer(recovered, dirty = true, source = UpdateSource.Editor))

		val scene = run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, ids.prologue.id))

		assertEquals("Before it all.", scene.markdown)
		assertEquals(listOf("Recovered edit."), scenes.getSceneTempBufferContents().map { it.markdown })
	}

	@Test
	fun `unreadable project metadata is listed without being rewritten`() = onTestThread {
		val def = get<ProjectsRepository>().findProject(PROJECT)!!
		val metadata = def.path.toOkioPath() / "project.toml"
		ffs.write(metadata) { writeUtf8("not = [toml") }

		val summary = run<NoInput, ProjectList>("project.list", NoInput).projects.single()

		assertNull(summary.created)
		assertEquals("not = [toml", ffs.read(metadata) { readUtf8() })
	}

	@Test
	fun `malformed dates are invalid input`() = onTestThread {
		val input = buildJsonObject {
			put("project", PROJECT)
			put("from", "2026-13-40")
		}
		val error = assertFailsWith<OperationException> { get<OperationRegistry>().dispatch("stats.activity", input) }
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	@Test
	fun `dispatch runs operations on JSON`() = onTestThread {
		val input = buildJsonObject {
			put("project", PROJECT)
			put("id", ids.prologue.id)
		}
		val output = get<OperationRegistry>().dispatch("scene.read", input).jsonObject

		assertEquals(JsonPrimitive("Before it all."), output["markdown"])
	}

	@Test
	fun `ops list offers export formats in the schema`() = onTestThread {
		val ops = run<NoInput, OperationList>("ops.list", NoInput)
		val export = ops.operations.single { it.name == "project.export" }
		val formats = export.input["properties"]!!.jsonObject["format"]!!.jsonObject["enum"]!!.jsonArray
		assertTrue(JsonPrimitive("markdown") in formats)
		assertTrue(ops.operations.all { it.agentVisible })

		val content = export.output["properties"]!!.jsonObject["content"]!!.jsonObject
		assertEquals(JsonPrimitive("base64"), content["contentEncoding"])
	}

	private companion object {
		const val PROJECT = "Storm Novel"
		val DAY_ONE = LocalDate(2026, 3, 1)
		val DAY_TWO = LocalDate(2026, 3, 2)
		val IMAGE = byteArrayOf(1, 2, 3, 4)
	}
}
