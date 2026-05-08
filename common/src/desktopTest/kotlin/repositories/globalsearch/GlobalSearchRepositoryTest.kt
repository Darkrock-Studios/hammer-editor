package repositories.globalsearch

import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsearchrepository.GlobalSearchRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class GlobalSearchRepositoryTest : BaseTest() {

	private val projectDef = ProjectDef(name = "Test", path = HPath("/projects/Test", "Test", false))

	private lateinit var sceneEditor: SceneEditorRepository
	private lateinit var notes: NotesRepository
	private lateinit var encyclopedia: EncyclopediaRepository
	private lateinit var timeLine: TimeLineRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		sceneEditor = mockk(relaxed = true)
		notes = mockk(relaxed = true)
		encyclopedia = mockk(relaxed = true)
		timeLine = mockk(relaxed = true)

		// Defaults: empty results
		every { sceneEditor.getScenes() } returns emptyList()
		every { sceneEditor.getSceneBuffer(any<SceneItem>()) } returns null
		every { notes.getNotes() } returns emptyList()
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(emptyList())
		every { encyclopedia.entryListFlow } returns MutableSharedFlow<List<EntryDef>>(
			replay = 1,
			extraBufferCapacity = 1,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		).apply { tryEmit(emptyList()) }
	}

	private fun createRepository() = GlobalSearchRepository(
		projectDef = projectDef,
		sceneEditor = sceneEditor,
		notes = notes,
		encyclopedia = encyclopedia,
		timeLine = timeLine,
	)

	@Test
	fun `findMatch returns null for empty text or query`() {
		assertNull(GlobalSearchRepository.findMatch("", "foo"))
		assertNull(GlobalSearchRepository.findMatch("hello", ""))
	}

	@Test
	fun `findMatch returns null when no match`() {
		assertNull(GlobalSearchRepository.findMatch("hello world", "xyz"))
	}

	@Test
	fun `findMatch is case insensitive`() {
		val match = GlobalSearchRepository.findMatch("Hello WORLD", "world")
		assertNotNull(match)
		assertEquals("Hello WORLD", match.text)
	}

	@Test
	fun `buildSnippet trims a window around match with ellipses`() {
		val text = "x".repeat(200) + "needle" + "y".repeat(200)
		val match = GlobalSearchRepository.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(match.text.startsWith("…"))
		assertTrue(match.text.endsWith("…"))
		assertEquals("needle", match.text.substring(match.matchStart, match.matchEnd))
	}

	@Test
	fun `buildSnippet does not add leading ellipsis when match is at start`() {
		val text = "needle" + "y".repeat(200)
		val match = GlobalSearchRepository.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(!match.text.startsWith("…"))
		assertEquals(0, match.matchStart)
	}

	@Test
	fun `buildSnippet collapses whitespace`() {
		val text = "before\n\n\tneedle  after"
		val match = GlobalSearchRepository.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(!match.text.contains("\n"))
		assertTrue(!match.text.contains("\t"))
	}

	@Test
	fun `setQuery shorter than min length clears results without searching`() = runTest {
		val repo = createRepository()
		repo.setQuery("a")
		advanceUntilIdle()

		assertEquals("a", repo.state.value.query)
		assertEquals(emptyList(), repo.state.value.results)
		assertTrue(!repo.state.value.isSearching)
	}

	@Test
	fun `setQuery searches notes content`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(NoteContent(id = 1, created = Clock.System.now(), content = "Once a hero rose")),
			NoteContainer(NoteContent(id = 2, created = Clock.System.now(), content = "Random text")),
		)

		val repo = createRepository()
		repo.setQuery("hero")
		advanceUntilIdle()

		val results = repo.state.value.results
		assertEquals(1, results.size)
		val note = results.first() as SearchResult.Note
		assertEquals(1, note.noteId)
		assertEquals("Once a hero rose", note.snippet.text)
	}

	@Test
	fun `setQuery prefers in-memory scene buffer over disk`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Battle",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)
		every { sceneEditor.getSceneBuffer(scene) } returns SceneBuffer(
			content = SceneContent(scene = scene, markdown = "Unsaved dragon attack"),
			source = UpdateSource.Editor,
		)
		// loadSceneMarkdownRaw should not be consulted because buffer wins
		every { sceneEditor.loadSceneMarkdownRaw(scene, any()) } returns "On-disk text"

		val repo = createRepository()
		repo.setQuery("dragon")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.Scene>()
		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("dragon"))
	}

	@Test
	fun `setQuery includes archived scenes`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 9,
			name = "Forgotten chapter",
			order = 0,
			archived = true,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)

		val repo = createRepository()
		repo.setQuery("Forgotten")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.Scene>()
		assertEquals(1, results.size)
		assertEquals(9, results.first().sceneItem.id)
	}

	@Test
	fun `setQuery matches encyclopedia entry name`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 11, type = EntryType.PERSON, name = "Aragorn")
		every { encyclopedia.entryListFlow } returns MutableSharedFlow<List<EntryDef>>(
			replay = 1,
			extraBufferCapacity = 1,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		).apply { tryEmit(listOf(def)) }
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)

		val repo = createRepository()
		repo.setQuery("aragorn")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.EncyclopediaEntry>()
		assertEquals(1, results.size)
		assertEquals(11, results.first().entryDef.id)
	}

	@Test
	fun `setQuery matches encyclopedia entry body when name does not`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 12, type = EntryType.PLACE, name = "Mordor")
		every { encyclopedia.entryListFlow } returns MutableSharedFlow<List<EntryDef>>(
			replay = 1,
			extraBufferCapacity = 1,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		).apply { tryEmit(listOf(def)) }
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)
		every { encyclopedia.loadEntry(def) } returns EntryContainer(
			EntryContent(
				id = 12,
				name = "Mordor",
				type = EntryType.PLACE,
				text = "A volcanic land. Sauron rules here.",
				tags = emptySet(),
			)
		)

		val repo = createRepository()
		repo.setQuery("Sauron")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.EncyclopediaEntry>()
		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("Sauron"))
	}

	@Test
	fun `setQuery matches timeline event content`() = runTest {
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(
			listOf(
				TimeLineEvent(id = 21, order = 0, date = "Year 3", content = "Coronation"),
				TimeLineEvent(id = 22, order = 1, date = null, content = "Boring meeting"),
			)
		)

		val repo = createRepository()
		repo.setQuery("coronation")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.TimelineEvent>()
		assertEquals(1, results.size)
		assertEquals(21, results.first().eventId)
	}

	@Test
	fun `parseQuery extracts tags and free text in any order`() {
		val parsed = GlobalSearchRepository.parseQuery("dragon #fantasy battle #adventure")
		assertEquals(listOf("fantasy", "adventure"), parsed.tags)
		assertEquals("dragon battle", parsed.text)
	}

	@Test
	fun `parseQuery handles tag-only and stray hash`() {
		val tagOnly = GlobalSearchRepository.parseQuery("#hero")
		assertEquals(listOf("hero"), tagOnly.tags)
		assertEquals("", tagOnly.text)

		val strayHash = GlobalSearchRepository.parseQuery("# foo")
		assertTrue(strayHash.tags.isEmpty())
		assertEquals("foo", strayHash.text)
	}

	@Test
	fun `setQuery filters notes by tag when only a tag is specified`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(
				NoteContent(
					id = 1,
					created = Clock.System.now(),
					content = "Once a hero rose",
					tags = setOf("fantasy"),
				)
			),
			NoteContainer(
				NoteContent(
					id = 2,
					created = Clock.System.now(),
					content = "Random text",
					tags = setOf("misc"),
				)
			),
		)

		val repo = createRepository()
		repo.setQuery("#fantasy")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.Note>()
		assertEquals(1, results.size)
		assertEquals(1, results.first().noteId)
	}

	@Test
	fun `setQuery combines tag filter with free text`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(
				NoteContent(
					id = 1,
					created = Clock.System.now(),
					content = "Dragon attack",
					tags = setOf("fantasy"),
				)
			),
			NoteContainer(
				NoteContent(
					id = 2,
					created = Clock.System.now(),
					content = "Dragon roars",
					tags = setOf("misc"),
				)
			),
			NoteContainer(
				NoteContent(
					id = 3,
					created = Clock.System.now(),
					content = "Sleepy village",
					tags = setOf("fantasy"),
				)
			),
		)

		val repo = createRepository()
		repo.setQuery("#fantasy dragon")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.Note>()
		assertEquals(1, results.size)
		assertEquals(1, results.first().noteId)
	}

	@Test
	fun `setQuery with tag excludes scenes`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Battle",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)
		every { notes.getNotes() } returns listOf(
			NoteContainer(
				NoteContent(
					id = 1,
					created = Clock.System.now(),
					content = "Battle plans",
					tags = setOf("plot"),
				)
			),
		)

		val repo = createRepository()
		repo.setQuery("#plot")
		advanceUntilIdle()

		val results = repo.state.value.results
		assertTrue(results.none { it is SearchResult.Scene })
		assertEquals(1, results.filterIsInstance<SearchResult.Note>().size)
	}

	@Test
	fun `setQuery exposes parsed tags on state`() = runTest {
		val repo = createRepository()
		repo.setQuery("#a #b text")
		advanceUntilIdle()

		assertEquals(listOf("a", "b"), repo.state.value.parsedTags)
		assertEquals("text", repo.state.value.parsedText)
	}

	@Test
	fun `setQuery debounces - only the latest query produces results`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(NoteContent(id = 1, created = Clock.System.now(), content = "alpha line")),
			NoteContainer(NoteContent(id = 2, created = Clock.System.now(), content = "bravo line")),
		)

		val repo = createRepository()
		repo.setQuery("alpha")
		advanceTimeBy(100)
		repo.setQuery("bravo")
		advanceUntilIdle()

		val results = repo.state.value.results.filterIsInstance<SearchResult.Note>()
		assertEquals(1, results.size)
		assertEquals(2, results.first().noteId)
	}
}
