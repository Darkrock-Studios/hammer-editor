package usecases.globalsearch

import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SearchProjectUseCaseTest : BaseTest() {

	private val projectDef = ProjectDef(name = "Test", path = HPath("/projects/Test", "Test", false))

	private lateinit var sceneEditor: SceneRepository
	private lateinit var sceneMetadataRepository: SceneMetadataRepository
	private lateinit var sceneContentRepository: SceneContentRepository
	private lateinit var notes: NotesRepository
	private lateinit var encyclopedia: EncyclopediaRepository
	private lateinit var timeLine: TimeLineRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		sceneEditor = mockk(relaxed = true)
		sceneMetadataRepository = mockk(relaxed = true)
		sceneContentRepository = mockk(relaxed = true)
		notes = mockk(relaxed = true)
		encyclopedia = mockk(relaxed = true)
		timeLine = mockk(relaxed = true)

		// Defaults: empty results
		every { sceneEditor.getScenes() } returns emptyList()
		every { sceneContentRepository.getSceneBuffer(any<SceneItem>()) } returns null
		coEvery { sceneMetadataRepository.loadSceneMetadata(any()) } returns SceneMetadata()
		every { notes.getNotes() } returns emptyList()
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(emptyList())
		coEvery { encyclopedia.ensureEntriesLoaded() } returns emptyList()
	}

	private fun createUseCase() = SearchProjectUseCase(
		sceneEditor = sceneEditor,
		sceneMetadataRepository = sceneMetadataRepository,
		sceneContentRepository = sceneContentRepository,
		notes = notes,
		encyclopedia = encyclopedia,
		timeLine = timeLine,
	)

	@Test
	fun `findMatch returns null for empty text or query`() {
		assertNull(SearchProjectUseCase.findMatch("", "foo"))
		assertNull(SearchProjectUseCase.findMatch("hello", ""))
	}

	@Test
	fun `findMatch returns null when no match`() {
		assertNull(SearchProjectUseCase.findMatch("hello world", "xyz"))
	}

	@Test
	fun `findMatch is case insensitive`() {
		val match = SearchProjectUseCase.findMatch("Hello WORLD", "world")
		assertNotNull(match)
		assertEquals("Hello WORLD", match.text)
	}

	@Test
	fun `buildSnippet trims a window around match with ellipses`() {
		val text = "x".repeat(200) + "needle" + "y".repeat(200)
		val match = SearchProjectUseCase.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(match.text.startsWith("…"))
		assertTrue(match.text.endsWith("…"))
		assertEquals("needle", match.text.substring(match.matchStart, match.matchEnd))
	}

	@Test
	fun `buildSnippet does not add leading ellipsis when match is at start`() {
		val text = "needle" + "y".repeat(200)
		val match = SearchProjectUseCase.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(!match.text.startsWith("…"))
		assertEquals(0, match.matchStart)
	}

	@Test
	fun `buildSnippet collapses whitespace`() {
		val text = "before\n\n\tneedle  after"
		val match = SearchProjectUseCase.findMatch(text, "needle")
		assertNotNull(match)
		assertTrue(!match.text.contains("\n"))
		assertTrue(!match.text.contains("\t"))
	}

	@Test
	fun `parseQuery extracts tags and free text in any order`() {
		val parsed = SearchProjectUseCase.parseQuery("dragon #fantasy battle #adventure")
		assertEquals(listOf("fantasy", "adventure"), parsed.tags)
		assertEquals("dragon battle", parsed.text)
	}

	@Test
	fun `parseQuery handles tag-only and stray hash`() {
		val tagOnly = SearchProjectUseCase.parseQuery("#hero")
		assertEquals(listOf("hero"), tagOnly.tags)
		assertEquals("", tagOnly.text)

		val strayHash = SearchProjectUseCase.parseQuery("# foo")
		assertTrue(strayHash.tags.isEmpty())
		assertEquals("foo", strayHash.text)
	}

	@Test
	fun `search with query shorter than min length returns nothing`() = runTest {
		val results = createUseCase().search("a", GlobalSearchFilter.All)
		assertEquals(emptyList(), results)
	}

	@Test
	fun `search finds notes content`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(NoteContent(id = 1, created = Clock.System.now(), content = "Once a hero rose")),
			NoteContainer(NoteContent(id = 2, created = Clock.System.now(), content = "Random text")),
		)

		val results = createUseCase().search("hero", GlobalSearchFilter.All)

		assertEquals(1, results.size)
		val note = results.first() as SearchResult.Note
		assertEquals(1, note.noteId)
		assertEquals("Once a hero rose", note.snippet.text)
	}

	@Test
	fun `search prefers in-memory scene buffer over disk`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Battle",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)
		every { sceneContentRepository.getSceneBuffer(scene) } returns SceneBuffer(
			content = SceneContent(scene = scene, markdown = "Unsaved dragon attack"),
			source = UpdateSource.Editor,
		)
		// loadSceneMarkdownRaw should not be consulted because buffer wins
		every { sceneEditor.loadSceneMarkdownRaw(scene, any()) } returns "On-disk text"

		val results = createUseCase().search("dragon", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("dragon"))
	}

	@Test
	fun `search includes archived scenes`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 9,
			name = "Forgotten chapter",
			order = 0,
			archived = true,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)

		val results = createUseCase().search("Forgotten", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertEquals(9, results.first().sceneItem.id)
	}

	@Test
	fun `search matches encyclopedia entry name`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 11, type = EntryType.PERSON, name = "Aragorn")
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)

		val results = createUseCase().search("aragorn", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(1, results.size)
		assertEquals(11, results.first().entryDef.id)
	}

	@Test
	fun `search matches encyclopedia entry body when name does not`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 12, type = EntryType.PLACE, name = "Mordor")
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

		val results = createUseCase().search("Sauron", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("Sauron"))
	}

	@Test
	fun `search matches timeline event content`() = runTest {
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(
			listOf(
				TimeLineEvent(id = 21, order = 0, date = "Year 3", content = "Coronation"),
				TimeLineEvent(id = 22, order = 1, date = null, content = "Boring meeting"),
			)
		)

		val results = createUseCase().search("coronation", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.TimelineEvent>()

		assertEquals(1, results.size)
		assertEquals(21, results.first().eventId)
	}

	@Test
	fun `search filters notes by tag when only a tag is specified`() = runTest {
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

		val results = createUseCase().search("#fantasy", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertEquals(1, results.first().noteId)
	}

	@Test
	fun `search combines tag filter with free text`() = runTest {
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

		val results = createUseCase().search("#fantasy dragon", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertEquals(1, results.first().noteId)
	}

	@Test
	fun `search with tag returns matching scenes by metadata`() = runTest {
		val taggedScene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Battle",
			order = 0,
		)
		val untaggedScene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 8,
			name = "Picnic",
			order = 1,
		)
		every { sceneEditor.getScenes() } returns listOf(taggedScene, untaggedScene)
		coEvery { sceneMetadataRepository.loadSceneMetadata(7) } returns SceneMetadata(tags = setOf("plot"))
		coEvery { sceneMetadataRepository.loadSceneMetadata(8) } returns SceneMetadata(tags = setOf("misc"))

		val results = createUseCase().search("#plot", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertEquals(7, results.first().sceneItem.id)
		assertTrue(results.first().title.contains("#plot"))
	}

	@Test
	fun `search combines tag filter with free text for scenes`() = runTest {
		val matchingScene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Dragon attack",
			order = 0,
		)
		val tagOnlyScene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 8,
			name = "Picnic",
			order = 1,
		)
		every { sceneEditor.getScenes() } returns listOf(matchingScene, tagOnlyScene)
		coEvery { sceneMetadataRepository.loadSceneMetadata(7) } returns SceneMetadata(tags = setOf("fantasy"))
		coEvery { sceneMetadataRepository.loadSceneMetadata(8) } returns SceneMetadata(tags = setOf("fantasy"))

		val results = createUseCase().search("#fantasy dragon", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertEquals(7, results.first().sceneItem.id)
	}

	@Test
	fun `search honors filter to a single source`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(NoteContent(id = 1, created = Clock.System.now(), content = "dragon note")),
		)
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "dragon scene",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)

		val results = createUseCase().search("dragon", GlobalSearchFilter.Notes)

		assertEquals(1, results.size)
		assertTrue(results.first() is SearchResult.Note)
	}
}
