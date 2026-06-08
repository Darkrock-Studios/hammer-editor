package repositories.tagindex

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tagindex.BuildTagIndexUseCase
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityRef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import getProject1Def
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
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class TagIndexServiceTest : BaseTest() {

	private val projectDef = getProject1Def()
	private lateinit var encyclopedia: EncyclopediaRepository
	private lateinit var notes: NotesRepository
	private lateinit var timeline: TimeLineRepository
	private lateinit var sceneEditor: SceneRepository
	private lateinit var sceneMetadata: SceneMetadataRepository

	private lateinit var entryContentChangedFlow: MutableSharedFlow<Unit>
	private lateinit var noteContentChangedFlow: MutableSharedFlow<Unit>
	private lateinit var eventContentChangedFlow: MutableSharedFlow<Unit>
	private lateinit var notesListFlow: MutableSharedFlow<List<NoteContainer>>
	private lateinit var timelineFlow: MutableSharedFlow<TimeLineContainer>
	private lateinit var sceneMetadataUpdateFlow: MutableSharedFlow<Pair<Int, SceneMetadata>>

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(module {})

		encyclopedia = mockk(relaxed = true)
		notes = mockk(relaxed = true)
		timeline = mockk(relaxed = true)
		sceneEditor = mockk(relaxed = true)
		sceneMetadata = mockk(relaxed = true)

		entryContentChangedFlow = unitFlow()
		noteContentChangedFlow = unitFlow()
		eventContentChangedFlow = unitFlow()
		notesListFlow = MutableSharedFlow(
			replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)
		timelineFlow = MutableSharedFlow(
			replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)
		sceneMetadataUpdateFlow = MutableSharedFlow(
			extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)

		every { encyclopedia.entryContentChangedFlow } returns entryContentChangedFlow
		every { notes.noteContentChangedFlow } returns noteContentChangedFlow
		every { timeline.eventContentChangedFlow } returns eventContentChangedFlow
		every { notes.notesListFlow } returns notesListFlow
		every { timeline.timelineFlow } returns timelineFlow
		every { sceneMetadata.metadataUpdateFlow } returns sceneMetadataUpdateFlow
		every { sceneEditor.getScenes() } returns emptyList()
		every { sceneEditor.getArchivedScenes() } returns emptyList()
	}

	private fun unitFlow() = MutableSharedFlow<Unit>(
		extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)

	private fun stubNotes(vararg note: NoteContent) {
		notesListFlow.tryEmit(note.map { NoteContainer(it) })
	}

	private fun stubTimeline(vararg event: TimeLineEvent) {
		timelineFlow.tryEmit(TimeLineContainer(events = event.toList()))
	}

	private fun stubEntries(vararg entry: EntryContent) {
		val defs = entry.map { it.toDef(projectDef) }
		coEvery { encyclopedia.ensureEntriesLoaded() } returns defs
		for (e in entry) {
			every { encyclopedia.loadEntry(e.toDef(projectDef)) } returns EntryContainer(e)
		}
	}

	private fun note(id: Int, vararg tags: String) =
		NoteContent(id = id, created = Clock.System.now(), content = "n$id", tags = tags.toSet())

	private fun event(id: Int, vararg tags: String) =
		TimeLineEvent(id = id, order = id, content = "e$id", tags = tags.toSet())

	private fun entry(id: Int, vararg tags: String) =
		EntryContent(
			id = id, name = "E$id", type = EntryType.PERSON, text = "", tags = tags.toSet(),
		)

	private fun scene(id: Int) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = "S$id",
		order = id,
	)

	private fun stubScenes(vararg sceneAndTags: Pair<Int, Set<String>>) {
		val items = sceneAndTags.map { scene(it.first) }
		every { sceneEditor.getScenes() } returns items
		every { sceneEditor.getArchivedScenes() } returns emptyList()
		for ((id, tags) in sceneAndTags) {
			coEvery { sceneMetadata.loadSceneMetadata(id) } returns SceneMetadata(tags = tags)
		}
	}

	private fun makeService(): TagIndexService {
		val buildTagIndex = BuildTagIndexUseCase(
			encyclopediaRepository = encyclopedia,
			notesRepository = notes,
			timeLineRepository = timeline,
			sceneEditorRepository = sceneEditor,
			sceneMetadataRepository = sceneMetadata,
		)
		return TagIndexService(
			projectDef = projectDef,
			encyclopediaRepository = encyclopedia,
			notesRepository = notes,
			timeLineRepository = timeline,
			sceneMetadataRepository = sceneMetadata,
			buildTagIndex = buildTagIndex,
		)
	}

	@Test
	fun `initial subscription builds index across all three sources`() =
		runTest(mainTestDispatcher) {
			stubNotes(note(1, "alpha", "beta"), note(2, "alpha"))
			stubTimeline(event(10, "beta", "gamma"))
			stubEntries(entry(100, "alpha", "gamma"))

			val service = makeService()
			advanceUntilIdle()

			val index = service.tagIndex.value

			assertEquals(
				setOf(
					TaggedEntityRef(TaggedEntityType.Note, 1),
					TaggedEntityRef(TaggedEntityType.Note, 2),
					TaggedEntityRef(TaggedEntityType.Encyclopedia, 100),
				),
				index.tagToEntities["alpha"],
			)
			assertEquals(
				setOf(
					TaggedEntityRef(TaggedEntityType.Note, 1),
					TaggedEntityRef(TaggedEntityType.TimelineEvent, 10),
				),
				index.tagToEntities["beta"],
			)
			assertEquals(
				setOf(
					TaggedEntityRef(TaggedEntityType.TimelineEvent, 10),
					TaggedEntityRef(TaggedEntityType.Encyclopedia, 100),
				),
				index.tagToEntities["gamma"],
			)
		}

	@Test
	fun `getRankedTags returns tags ordered by frequency`() = runTest(mainTestDispatcher) {
		stubNotes(note(1, "alpha", "beta"), note(2, "alpha"))
		stubTimeline(event(10, "beta", "gamma"))
		stubEntries(entry(100, "alpha"))

		val service = makeService()
		advanceUntilIdle()

		val ranked = service.getRankedTags()
		assertEquals("alpha", ranked[0].tag)
		assertEquals(3, ranked[0].count)
		assertEquals("beta", ranked[1].tag)
		assertEquals(2, ranked[1].count)
		assertEquals("gamma", ranked[2].tag)
		assertEquals(1, ranked[2].count)
	}

	@Test
	fun `getRankedTags by type only counts that entity type`() = runTest(mainTestDispatcher) {
		stubNotes(note(1, "alpha", "beta"), note(2, "alpha"))
		stubTimeline(event(10, "alpha"))
		stubEntries(entry(100, "alpha"))

		val service = makeService()
		advanceUntilIdle()

		val noteRanked = service.getRankedTags(TaggedEntityType.Note)
		assertEquals(listOf("alpha" to 2, "beta" to 1), noteRanked.map { it.tag to it.count })

		val timelineRanked = service.getRankedTags(TaggedEntityType.TimelineEvent)
		assertEquals(listOf("alpha" to 1), timelineRanked.map { it.tag to it.count })
	}

	@Test
	fun `getEntitiesWithTag returns the exact ref set`() = runTest(mainTestDispatcher) {
		stubNotes(note(1, "alpha"), note(2, "beta"))
		stubTimeline(event(10, "alpha"))
		stubEntries(entry(100, "alpha"))

		val service = makeService()
		advanceUntilIdle()

		assertEquals(
			setOf(
				TaggedEntityRef(TaggedEntityType.Note, 1),
				TaggedEntityRef(TaggedEntityType.TimelineEvent, 10),
				TaggedEntityRef(TaggedEntityType.Encyclopedia, 100),
			),
			service.getEntitiesWithTag("alpha"),
		)
		assertEquals(
			setOf(TaggedEntityRef(TaggedEntityType.Note, 2)),
			service.getEntitiesWithTag("beta"),
		)
		assertEquals(emptySet(), service.getEntitiesWithTag("nope"))
	}

	@Test
	fun `suggest filters by prefix case-insensitively and ranks by count`() =
		runTest(mainTestDispatcher) {
			stubNotes(note(1, "Aragorn", "arwen"), note(2, "Aragorn"))
			stubTimeline(event(10, "boromir"))
			stubEntries(entry(100, "arwen"))

			val service = makeService()
			advanceUntilIdle()

			val results = service.suggest("ar")
			assertEquals(listOf("Aragorn" to 2, "arwen" to 2), results.map { it.tag to it.count })

			val empty = service.suggest("zzz")
			assertTrue(empty.isEmpty())
		}

	@Test
	fun `rebuild fires when a content-changed signal is emitted`() = runTest(mainTestDispatcher) {
		stubNotes(note(1, "alpha"))
		stubTimeline()
		stubEntries()

		val service = makeService()
		advanceUntilIdle()
		assertEquals(1, service.tagIndex.value.tagToEntities["alpha"]?.size)

		stubNotes(note(1, "alpha"), note(2, "alpha"))
		noteContentChangedFlow.tryEmit(Unit)
		advanceUntilIdle()

		assertEquals(2, service.tagIndex.value.tagToEntities["alpha"]?.size)
	}

	@Test
	fun `initial subscription includes scenes with tags`() = runTest(mainTestDispatcher) {
		stubNotes()
		stubTimeline()
		stubEntries()
		stubScenes(
			1 to setOf("alpha", "scene-tag"),
			2 to setOf("alpha"),
		)

		val service = makeService()
		advanceUntilIdle()

		val index = service.tagIndex.value
		assertEquals(
			setOf(
				TaggedEntityRef(TaggedEntityType.Scene, 1),
				TaggedEntityRef(TaggedEntityType.Scene, 2),
			),
			index.tagToEntities["alpha"],
		)
		assertEquals(
			setOf(TaggedEntityRef(TaggedEntityType.Scene, 1)),
			index.tagToEntities["scene-tag"],
		)

		val sceneRanked = service.getRankedTags(TaggedEntityType.Scene)
		assertEquals(listOf("alpha" to 2, "scene-tag" to 1), sceneRanked.map { it.tag to it.count })
	}

	@Test
	fun `scene metadata update triggers rebuild`() = runTest(mainTestDispatcher) {
		stubNotes()
		stubTimeline()
		stubEntries()
		stubScenes(1 to setOf("alpha"))

		val service = makeService()
		advanceUntilIdle()
		assertEquals(1, service.tagIndex.value.tagToEntities["alpha"]?.size)

		stubScenes(1 to setOf("alpha"), 2 to setOf("alpha"))
		sceneMetadataUpdateFlow.tryEmit(2 to SceneMetadata(tags = setOf("alpha")))
		advanceUntilIdle()

		assertEquals(2, service.tagIndex.value.tagToEntities["alpha"]?.size)
	}

	@Test
	fun `two emissions within the debounce window coalesce to a single rebuild`() =
		runTest(mainTestDispatcher) {
			stubNotes(note(1, "alpha"))
			stubTimeline()
			stubEntries()

			val service = makeService()
			advanceUntilIdle()
			val before = service.tagIndex.value

			stubNotes(note(1, "alpha"), note(2, "alpha"))
			noteContentChangedFlow.tryEmit(Unit)
			advanceTimeBy(50)
			noteContentChangedFlow.tryEmit(Unit)
			advanceTimeBy(50)

			// Still inside the 150ms debounce window — no rebuild yet
			assertEquals(before, service.tagIndex.value)

			advanceUntilIdle()
			assertEquals(2, service.tagIndex.value.tagToEntities["alpha"]?.size)
		}
}
