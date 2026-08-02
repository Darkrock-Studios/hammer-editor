package usecases.globalsearch

import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
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
	fun `search matches across a backslash escape`() = runTest {
		every { notes.getNotes() } returns listOf(note(1, "A well\\-known secret"))

		val results = createUseCase().search("well-known", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
	}

	@Test
	fun `snippets render escaped text the way it reads on screen`() = runTest {
		every { notes.getNotes() } returns listOf(note(1, "A well\\-known garden\\! at dusk"))

		val results = createUseCase().search("well-known", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		val snippet = results.first().snippet
		assertEquals("A well-known garden! at dusk", snippet.text)
		assertEquals("well-known", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
	}

	@Test
	fun `the title reads the same way as the snippet under it`() = runTest {
		every { notes.getNotes() } returns listOf(note(1, "Alice \\(the elder\\) is well\\-known\\!"))

		val results = createUseCase().search("well-known", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertEquals("Alice (the elder) is well-known!", results.first().title)
		assertEquals("Alice (the elder) is well-known!", results.first().snippet.text)
	}

	@Test
	fun `escaped markers are searchable as literal characters`() = runTest {
		every { notes.getNotes() } returns listOf(note(1, "the \\_shape\\_ of it"))

		val results = createUseCase().search("_shape_", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("_shape_"))
	}

	@Test
	fun `searching for a literal backslash escape still finds it`() = runTest {
		every { notes.getNotes() } returns listOf(note(1, "raw well\\-known marker"))

		val results = createUseCase().search("well\\-known", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
	}

	@Test
	fun `unescaped markup is left alone so literal text is not rewritten`() = runTest {
		every { notes.getNotes() } returns listOf(
			note(1, "The cost is 5*4"),
			note(2, "the user_name field"),
		)

		val math = createUseCase().search("5*4", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()
		val identifier = createUseCase().search("user_name", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, math.size)
		assertEquals("The cost is 5*4", math.first().snippet.text)
		assertEquals(1, identifier.size)
		assertTrue(identifier.first().snippet.text.contains("user_name"))
	}

	@Test
	fun `scene bodies match across a backslash escape`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "Opening",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)
		every { sceneContentRepository.getSceneBuffer(scene) } returns null
		every { sceneEditor.loadSceneMarkdownRaw(scene, any()) } returns "A well\\-known road"

		val results = createUseCase().search("well-known", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertEquals("A well-known road", results.first().snippet.text)
	}

	@Test
	fun `timeline dates are not unescaped`() = runTest {
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(
			listOf(TimeLineEvent(id = 21, order = 0, date = "1990\\-2000", content = "A long war")),
		)

		val verbatim = createUseCase().search("1990\\-2000", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.TimelineEvent>()
		val resolvedForm = createUseCase().search("1990-2000", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.TimelineEvent>()

		assertEquals(1, verbatim.size)
		// Unescaping the date would make this hit and would show a date the event does not have.
		assertEquals(0, resolvedForm.size)
	}

	@Test
	fun `tag-only search previews escaped text as it reads`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(
				NoteContent(
					id = 1,
					created = Clock.System.now(),
					content = "A well\\-known secret",
					tags = setOf("fantasy"),
				)
			),
		)

		val results = createUseCase().search("#fantasy", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertEquals("A well-known secret", results.first().snippet.text)
	}

	@Test
	fun `tag-only search returns a note with no content`() = runTest {
		every { notes.getNotes() } returns listOf(
			NoteContainer(
				NoteContent(
					id = 1,
					created = Clock.System.now(),
					content = "",
					tags = setOf("fantasy"),
				)
			),
		)

		val results = createUseCase().search("#fantasy", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Note>()

		assertEquals(1, results.size)
		assertEquals(1, results.first().noteId)
	}

	@Test
	fun `tag-only search returns an encyclopedia entry with no body text`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 13, type = EntryType.PERSON, name = "Alice")
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)
		every { encyclopedia.loadEntry(def) } returns EntryContainer(
			EntryContent(
				id = 13,
				name = "Alice",
				type = EntryType.PERSON,
				text = "",
				tags = setOf("fantasy"),
			)
		)

		val results = createUseCase().search("#fantasy", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(1, results.size)
		assertEquals(13, results.first().entryDef.id)
		assertTrue(results.first().title.contains("Alice"))
		assertEquals("Alice", results.first().snippet.text)
	}

	@Test
	fun `tag-only search returns timeline events with and without content`() = runTest {
		coEvery { timeLine.loadTimeline() } returns TimeLineContainer(
			listOf(
				TimeLineEvent(id = 21, order = 0, date = null, content = "", tags = setOf("fantasy")),
				TimeLineEvent(id = 22, order = 1, date = "Year 3", content = "Coronation", tags = setOf("fantasy")),
			)
		)

		val results = createUseCase().search("#fantasy", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.TimelineEvent>()

		assertEquals(listOf(21, 22), results.map { it.eventId })
		assertTrue(results.last().snippet.text.contains("Coronation"))
	}

	@Test
	fun `tag search combined with free text matches an encyclopedia entry name`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 14, type = EntryType.PERSON, name = "Alice")
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)
		every { encyclopedia.loadEntry(def) } returns EntryContainer(
			EntryContent(
				id = 14,
				name = "Alice",
				type = EntryType.PERSON,
				text = "A traveler from the north.",
				tags = setOf("fantasy"),
			)
		)

		val results = createUseCase().search("#fantasy alice", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(1, results.size)
		assertEquals(14, results.first().entryDef.id)
		assertTrue(results.first().snippet.text.contains("Alice"))
	}

	@Test
	fun `tag search with free text falls through to the encyclopedia entry body`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 15, type = EntryType.PLACE, name = "Mordor")
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)
		every { encyclopedia.loadEntry(def) } returns EntryContainer(
			EntryContent(
				id = 15,
				name = "Mordor",
				type = EntryType.PLACE,
				text = "Sauron rules here.",
				tags = setOf("fantasy"),
			)
		)

		val results = createUseCase().search("#fantasy sauron", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(1, results.size)
		assertTrue(results.first().snippet.text.contains("Sauron"))
	}

	@Test
	fun `tag search drops an encyclopedia entry when the free text matches neither name nor body`() = runTest {
		val def = EntryDef(projectDef = projectDef, id = 16, type = EntryType.PERSON, name = "Alice")
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(def)
		every { encyclopedia.loadEntry(def) } returns EntryContainer(
			EntryContent(
				id = 16,
				name = "Alice",
				type = EntryType.PERSON,
				text = "A traveler from the north.",
				tags = setOf("fantasy"),
			)
		)

		val results = createUseCase().search("#fantasy dragon", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.EncyclopediaEntry>()

		assertEquals(0, results.size)
	}

	@Test
	fun `tag-only search returns a scene with a blank name`() = runTest {
		val scene = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 7,
			name = "  ",
			order = 0,
		)
		every { sceneEditor.getScenes() } returns listOf(scene)
		coEvery { sceneMetadataRepository.loadSceneMetadata(7) } returns SceneMetadata(tags = setOf("plot"))

		val results = createUseCase().search("#plot", GlobalSearchFilter.All)
			.filterIsInstance<SearchResult.Scene>()

		assertEquals(1, results.size)
		assertEquals(7, results.first().sceneItem.id)
	}

	private fun note(id: Int, content: String) =
		NoteContainer(NoteContent(id = id, created = Clock.System.now(), content = content))

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
