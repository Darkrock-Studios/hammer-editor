package repositories.timeline

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.PlacedSubject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * Drives [TimelineService] over real repositories on a [FakeFileSystem], seeded with Test Project 1:
 * scenes 1, 3 to 7 and chapter 2 holding scenes 3 to 5.
 */
class TimelineServiceTest : BaseTest() {

	private val projDef = getProject1Def()

	@MockK
	private lateinit var syncJournal: SyncJournal

	@MockK
	private lateinit var idAllocator: IdAllocator

	@MockK(relaxed = true)
	private lateinit var syncJournalDatasource: SyncJournalDatasource

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var timeLineRepository: TimeLineRepository
	private lateinit var sceneRepository: SceneRepository
	private lateinit var sceneMetadataRepository: SceneMetadataRepository
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private var nextId = 100

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { syncJournal }
			single { syncJournalDatasource }
		})

		nextId = 100
		coEvery { idAllocator.claimNextId() } answers { nextId++ }
		coEvery { idAllocator.findNextId() } just Runs
		every { syncJournal.isServerSynchronized() } returns false
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { syncJournal.markEntityAsDirty(any()) } just Runs
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs

		createProject(ffs, PROJECT_1_NAME)
	}

	private suspend fun createService(
		wrapEncyclopedia: (EncyclopediaRepository) -> EncyclopediaRepository = { it },
	): TimelineService {
		val sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projDef)
		sceneRepository = SceneRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			syncJournal = syncJournal,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = SceneDatasource(projDef, ffs),
			clock = Clock.System,
		).initializeSceneEditor()
		sceneMetadataRepository = SceneMetadataRepository(
			projectDef = projDef,
			sceneMetadataDatasource = sceneMetadataDatasource,
			projectMetadataDatasource = mockk(relaxed = true),
			strRes = mockk(relaxed = true),
			clock = Clock.System,
		)
		encyclopediaRepository = EncyclopediaRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			datasource = EncyclopediaDatasource(projectDef = projDef, toml = toml, fileSystem = ffs),
			syncJournal = syncJournal,
		)
		timeLineRepository = TimeLineRepository(projDef, idAllocator, TimeLineDatasource(ffs, toml))

		encyclopediaRepository.createEntry("Alice", EntryType.PERSON, "A curious girl.", emptySet(), forceId = ALICE)
		encyclopediaRepository.createEntry("Wonderland", EntryType.PLACE, "Below.", emptySet(), forceId = WONDERLAND)
		encyclopediaRepository.createEntry(
			"Tea Party",
			EntryType.EVENT,
			"The **Hatter** pours tea.",
			emptySet(),
			forceId = TEA_PARTY,
			images = listOf(77, 78),
		)
		encyclopediaRepository.createEntry("Bob", EntryType.PERSON, "", emptySet(), forceId = BOB)
		encyclopediaRepository.createEntry("Teapot", EntryType.THING, "", emptySet(), forceId = TEAPOT)

		return TimelineService(
			timeLineRepository = timeLineRepository,
			projectDataRepository = ProjectDataRepository(ProjectDataDatasource(ffs, toml, projDef), projDef),
			sceneRepository = sceneRepository,
			sceneMetadataRepository = sceneMetadataRepository,
			encyclopediaRepository = wrapEncyclopedia(encyclopediaRepository),
		)
	}

	private fun date(vararg rungs: Pair<String, Int>) =
		StoryDate(rungs.associateTo(LinkedHashMap()) { (unit, value) -> unit to value.toLong() })

	private fun TimelineResult.event(): TimeLineEvent = assertIs<TimelineResult.Ok>(this).event!!

	private fun TimelineResult.error(): TimeLineEventError = assertIs<TimelineResult.Invalid>(this).error

	private fun TimelineDefResult.def(): TimelineDef = assertIs<TimelineDefResult.Ok>(this).def

	private fun TimelineDefResult.error(): TimeLineEventError = assertIs<TimelineDefResult.Invalid>(this).error

	private suspend fun TimelineService.datedTimeline(name: String, calendar: CalendarDef = CalendarDef()): TimelineDef =
		createTimeline(name, TimelineMode.DATED, calendar).def()

	private suspend fun storedData() = ProjectDataDatasource(ffs, toml, projDef).load().data

	/** Writes [timelines] to settings.toml as sync or a hand edit could, bypassing the service's rules. */
	private suspend fun storeTimelines(vararg timelines: TimelineDef) =
		ProjectDataDatasource(ffs, toml, projDef).save(StoredProjectData(ProjectData(timelines = timelines.toList())))

	private suspend fun TimelineService.datedStory(calendar: CalendarDef = CalendarDef()) {
		assertEquals(TimelineResult.Ok(null), updateDefinition(TimelineDef.MAIN_ID) { it.copy(mode = TimelineMode.DATED, calendar = calendar) })
	}

	private suspend fun stored(id: Int): TimeLineEvent = timeLineRepository.currentEvents().first { it.id == id }

	@Test
	fun `a subject can be placed only once`() = runTest(mainTestDispatcher) {
		val service = createService()

		val placed = service.placeScene(4, start = null, end = null, lane = null, milestone = false, timeline = TimelineDef.MAIN_ID).event()

		assertEquals(TimelineKind.SCENE, placed.kind)
		assertEquals(4, placed.subject)
		assertEquals("Scene ID 4", placed.cachedTitle)
		assertEquals(
			TimeLineEventError.DUPLICATE_PLACEMENT,
			service.placeScene(4, start = null, end = null, lane = null, milestone = false, timeline = TimelineDef.MAIN_ID).error(),
		)
	}

	@Test
	fun `an archived scene cannot be placed or referenced`() = runTest(mainTestDispatcher) {
		val service = createService()
		sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)

		assertEquals(TimeLineEventError.SUBJECT_ARCHIVED, service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).error())
		assertEquals(
			TimeLineEventError.REFERENCE_NOT_FOUND,
			service.createEvent(TimelineEventDraft(title = "Storm", references = setOf(4))).error(),
		)
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `a placed scene that is archived resolves as archived until it is unarchived`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()

		sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)

		val archived = assertIs<SubjectSummary.Archived>(service.resolveSubject(stored(placed.id)))
		assertEquals(4, archived.scene.id)
		assertEquals("Scene ID 4", service.displayTitle(stored(placed.id)))

		sceneRepository.unarchiveScene(sceneRepository.getArchivedSceneFromId(4)!!)

		assertIs<SubjectSummary.Scene>(service.resolveSubject(stored(placed.id)))
	}

	@Test
	fun `only EVENT entries can be placed`() = runTest(mainTestDispatcher) {
		val service = createService()

		assertEquals(
			TimeLineEventError.SUBJECT_WRONG_TYPE,
			service.placeEntry(ALICE, start = null, end = null, lane = null, milestone = false, timeline = TimelineDef.MAIN_ID).error(),
		)
		val placed = service.placeEntry(TEA_PARTY, start = null, end = null, lane = "Hatter", milestone = true, timeline = TimelineDef.MAIN_ID).event()
		assertEquals(TimelineKind.ENTRY, placed.kind)
		assertEquals("Tea Party", placed.cachedTitle)
		assertEquals(true, placed.milestone)
	}

	@Test
	fun `placing a missing subject or a chapter fails`() = runTest(mainTestDispatcher) {
		val service = createService()

		assertEquals(TimeLineEventError.SUBJECT_NOT_FOUND, service.placeScene(999, null, null, null, false, timeline = TimelineDef.MAIN_ID).error())
		assertEquals(TimeLineEventError.SUBJECT_NOT_FOUND, service.placeEntry(999, null, null, null, false, timeline = TimelineDef.MAIN_ID).error())
		assertEquals(TimeLineEventError.SUBJECT_WRONG_TYPE, service.placeScene(2, null, null, null, false, timeline = TimelineDef.MAIN_ID).error())
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `a placement takes its label in the same write`() = runTest(mainTestDispatcher) {
		val service = createService()
		val tooLong = "x".repeat(TimelineLimits.MAX_LABEL + 1)

		assertEquals(TimeLineEventError.LABEL_TOO_LONG, service.placeScene(4, null, null, null, false, TimelineDef.MAIN_ID, tooLong).error())
		assertEquals(TimeLineEventError.LABEL_TOO_LONG, service.placeEntry(TEA_PARTY, null, null, null, false, TimelineDef.MAIN_ID, tooLong).error())
		assertEquals(emptyList(), timeLineRepository.currentEvents())

		val placed = service.placeScene(4, null, null, null, false, label = "At dawn", timeline = TimelineDef.MAIN_ID).event()
		assertEquals("At dawn", stored(placed.id).label)
	}

	@Test
	fun `references must name a scene or an entry`() = runTest(mainTestDispatcher) {
		val service = createService()

		val created = service.createEvent(TimelineEventDraft(title = "Storm", references = setOf(4, ALICE))).event()
		assertEquals(setOf(4, ALICE), stored(created.id).references)

		assertEquals(
			TimeLineEventError.REFERENCE_NOT_FOUND,
			service.createEvent(TimelineEventDraft(references = setOf(4, 999))).error(),
		)
		assertEquals(
			TimeLineEventError.REFERENCE_NOT_FOUND,
			service.createEvent(TimelineEventDraft(references = setOf(2))).error(),
		)
		assertEquals(
			TimeLineEventError.SELF_REFERENCE,
			service.createEvent(TimelineEventDraft(id = 60, references = setOf(60))).error(),
		)
		assertEquals(
			TimeLineEventError.SUBJECT_NOT_ALLOWED,
			service.createEvent(TimelineEventDraft(kind = TimelineKind.SCENE, subject = 4)).error(),
		)
	}

	@Test
	fun `a dated timeline requires a start`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()

		assertEquals(TimeLineEventError.DATE_REQUIRED, service.createEvent(TimelineEventDraft(title = "Undated")).error())
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).error())

		val placed = service.placeScene(4, date("year" to 97), null, null, false, timeline = TimelineDef.MAIN_ID).event()
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.setDates(placed.id, start = null, end = null).error())
	}

	@Test
	fun `dates must suit the calendar and end no earlier than the start`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()
		val start = date("year" to 1, "day" to 10, "hour" to 5)

		assertEquals(
			TimeLineEventError.END_BEFORE_START,
			service.createEvent(TimelineEventDraft(start = start, end = date("year" to 1, "day" to 9))).error(),
		)
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.createEvent(TimelineEventDraft(start = date("year" to 1, "month" to 2))).error(),
		)
		assertEquals(
			TimeLineEventError.DATE_REQUIRED,
			service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = null) }.error(),
		)

		// A day-precise end may close a start later that same day.
		val created = service.createEvent(TimelineEventDraft(start = start, end = date("year" to 1, "day" to 10))).event()
		assertEquals(date("year" to 1, "day" to 10), stored(created.id).end)
	}

	@Test
	fun `lanes are trimmed on user writes`() = runTest(mainTestDispatcher) {
		val service = createService()

		val created = service.createEvent(TimelineEventDraft(title = "Storm", lane = "  Keep  ")).event()
		assertEquals("Keep", stored(created.id).lane)

		service.updateEvent(stored(created.id).copy(lane = " Alice ")).event()
		assertEquals("Alice", stored(created.id).lane)

		service.setLane(created.id, "   ").event()
		assertNull(stored(created.id).lane)

		val placed = service.placeScene(4, null, null, lane = " Queen ", milestone = false, timeline = TimelineDef.MAIN_ID).event()
		assertEquals("Queen", stored(placed.id).lane)
	}

	@Test
	fun `a timeline holds at most five lanes`() = runTest(mainTestDispatcher) {
		val service = createService()
		val events = listOf("A", "B", "C", "D", "E").map { lane ->
			service.createEvent(TimelineEventDraft(title = "In $lane", lane = lane)).event()
		}
		val alsoInC = service.createEvent(TimelineEventDraft(title = "Also in C", lane = "C")).event()
		service.createEvent(TimelineEventDraft(title = "Unlaned")).event()

		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.createEvent(TimelineEventDraft(title = "Sixth", lane = "F")).error(),
		)
		assertEquals(TimeLineEventError.TOO_MANY_LANES, service.setLane(alsoInC.id, "F").error())
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.placeScene(4, null, null, lane = "F", milestone = false, timeline = TimelineDef.MAIN_ID).error(),
		)
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.updateEvent(stored(alsoInC.id).copy(lane = "F")).error(),
		)
		assertEquals(TimeLineEventError.TOO_MANY_LANES, service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(laneOrder = listOf("F")) }.error())

		// Renaming E's only entry to F trades one lane for another.
		service.setLane(events[4].id, "F").event()
		assertEquals(listOf("A", "B", "C", "D", "F"), service.lanes(TimelineDef.MAIN_ID))
	}

	@Test
	fun `each timeline counts its own five lanes`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		listOf("A", "B", "C", "D", "E").forEach { lane ->
			service.createEvent(TimelineEventDraft(title = "Main $lane", lane = lane)).event()
		}
		val onBackstory = listOf("F", "G", "H", "I", "J").map { lane ->
			service.createEvent(TimelineEventDraft(title = "Backstory $lane", lane = lane, timeline = backstory.id)).event()
		}

		assertEquals(listOf("F", "G", "H", "I", "J"), service.lanes(backstory.id))
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.createEvent(TimelineEventDraft(title = "Sixth", lane = "K", timeline = backstory.id)).error(),
		)
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.updateDefinition(backstory.id) { it.copy(laneOrder = listOf("K")) }.error(),
		)
		// A lane Main already has is still a sixth one on Backstory.
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.createEvent(TimelineEventDraft(title = "Main lane", lane = "A", timeline = backstory.id)).error(),
		)
		// Main has room for none either, though Backstory's lanes are not Main's.
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.createEvent(TimelineEventDraft(title = "Backstory lane", lane = "F")).error(),
		)
		// Trading Backstory's only F entry for K keeps it at five.
		service.setLane(onBackstory[0].id, "K").event()
	}

	@Test
	fun `a move refuses a lane that would be the target's sixth`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		listOf("A", "B", "C", "D", "E").forEach { lane ->
			service.createEvent(TimelineEventDraft(title = "Backstory $lane", lane = lane, timeline = backstory.id)).event()
		}
		val sharedLane = service.createEvent(TimelineEventDraft(title = "Shared", lane = "C")).event()
		val newLane = service.createEvent(TimelineEventDraft(title = "New", lane = "F")).event()

		assertEquals(TimeLineEventError.TOO_MANY_LANES, service.moveToTimeline(newLane.id, backstory.id).error())
		assertNull(stored(newLane.id).timeline)
		service.moveToTimeline(sharedLane.id, backstory.id).event()
		assertEquals(backstory.id, stored(sharedLane.id).timeline)
	}

	@Test
	fun `moving stragglers leaves one whose lane would be the target's sixth`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		listOf("A", "B", "C", "D").forEach { lane ->
			service.createEvent(TimelineEventDraft(title = "Backstory $lane", lane = lane, timeline = backstory.id)).event()
		}
		// As sync stores events made offline on a timeline deleted since.
		val fifth = timeLineRepository.createEvent(TimelineEventDraft(title = "Fifth", lane = "E", timeline = "zzzz9999"))
		val sixth = timeLineRepository.createEvent(TimelineEventDraft(title = "Sixth", lane = "F", timeline = "zzzz9999"))

		assertEquals(1, service.moveOrphans(backstory.id))

		assertEquals(backstory.id, stored(fifth.id).timeline)
		assertEquals(listOf(sixth.id), service.orphansFlow.first().map { it.id })
	}

	@Test
	fun `a new timeline starts with at most five lanes`() = runTest(mainTestDispatcher) {
		val service = createService()

		assertEquals(
			TimelineDefResult.Invalid(TimeLineEventError.TOO_MANY_LANES),
			service.createTimeline("Backstory", laneOrder = listOf("A", "B", "C", "D", "E", "F")),
		)
		service.createTimeline("Backstory", laneOrder = listOf("A", "B", "C", "D", "E")).def()
	}

	@Test
	fun `a timeline already past five lanes keeps working but gains no lane`() = runTest(mainTestDispatcher) {
		val service = createService()
		// Two devices can each add a lane offline; sync then leaves six.
		val events = listOf("A", "B", "C", "D", "E", "F").map { lane ->
			timeLineRepository.createEvent(TimelineEventDraft(title = "In $lane", lane = lane))
		}

		service.setLabel(events[0].id, "Dawn").event()
		service.setLane(events[0].id, "B").event()
		service.createEvent(TimelineEventDraft(title = "Also in C", lane = "C")).event()
		assertEquals(TimeLineEventError.TOO_MANY_LANES, service.setLane(events[1].id, "G").error())
	}

	@Test
	fun `updateEvent rejects a kind or subject change`() = runTest(mainTestDispatcher) {
		val service = createService()
		val created = service.createEvent(TimelineEventDraft(title = "Storm")).event()

		assertEquals(
			TimeLineEventError.SUBJECT_NOT_ALLOWED,
			service.updateEvent(created.copy(kind = TimelineKind.SCENE, subject = 4)).error(),
		)
		assertEquals(
			TimeLineEventError.EVENT_NOT_FOUND,
			service.updateEvent(created.copy(id = 999)).error(),
		)
	}

	@Test
	fun `a placement's label is set, kept, and cleared`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, start = null, end = null, lane = null, milestone = false, timeline = TimelineDef.MAIN_ID).event()

		service.updatePlacement(placed.id, start = null, end = null, lane = "Alice", milestone = false, label = "Dusk").event()
		assertEquals("Alice" to "Dusk", stored(placed.id).lane to stored(placed.id).label)

		service.updatePlacement(placed.id, start = null, end = null, lane = "Queen", milestone = true).event()
		assertEquals("Dusk", stored(placed.id).label)

		service.updateEvent(stored(placed.id).copy(label = "Dawn", title = "Ignored")).event()
		assertEquals("Dawn", stored(placed.id).label)
		assertNull(stored(placed.id).title)

		service.setLabel(placed.id, null).event()
		assertNull(stored(placed.id).label)
	}

	@Test
	fun `moving an unknown event or to a position past the end is invalid`() = runTest(mainTestDispatcher) {
		val service = createService()
		val first = service.createEvent(TimelineEventDraft(title = "First")).event()
		service.createEvent(TimelineEventDraft(title = "Second")).event()

		assertEquals(
			TimeLineEventError.EVENT_NOT_FOUND,
			service.moveEvent(first.copy(id = 999), toIndex = 0, after = false).error(),
		)
		assertEquals(TimeLineEventError.MOVE_FAILED, service.moveEvent(first, toIndex = 2, after = false).error())
		assertEquals(first.id, service.moveEvent(first, toIndex = 0, after = false).event().id)
	}

	@Test
	fun `a move position counts only the event's own timeline`() = runTest(mainTestDispatcher) {
		val service = createService()
		val first = service.createEvent(TimelineEventDraft(title = "First")).event()
		service.createEvent(TimelineEventDraft(title = "Second")).event()
		val other = service.createTimeline("Backstory").def()
		service.createEvent(TimelineEventDraft(title = "Elsewhere", timeline = other.id)).event()

		assertEquals(TimeLineEventError.MOVE_FAILED, service.moveEvent(first, toIndex = 2, after = false).error())
		service.moveEvent(first, toIndex = 1, after = true).event()
		val main = timeLineRepository.currentEvents().filter { it.timeline == null }.sortedBy { it.order }
		assertEquals(listOf("Second", "First"), main.map { it.title })
	}

	@Test
	fun `switchToDated spaces undated events evenly and derives the order from the dates`() =
		runTest(mainTestDispatcher) {
			val service = createService()
			val a = service.createEvent(TimelineEventDraft(title = "A")).event()
			val b = service.createEvent(TimelineEventDraft(title = "B", start = date("year" to 5))).event()
			val c = service.createEvent(TimelineEventDraft(title = "C")).event()

			assertEquals(TimelineResult.Ok(null), service.switchToDated(TimelineDef.MAIN_ID, CalendarDef(), spacing = null))

			assertEquals(date("year" to 0), stored(a.id).start)
			assertEquals(date("year" to 5), stored(b.id).start)
			assertEquals(date("year" to 2), stored(c.id).start)
			val view = service.timelineFlow(TimelineDef.MAIN_ID).first()
			assertEquals(TimelineMode.DATED, view.def.mode)
			assertEquals(listOf(a.id, c.id, b.id), view.events.map { it.id })
		}

	@Test
	fun `switchToDated uses the given spacing`() = runTest(mainTestDispatcher) {
		val service = createService()
		val a = service.createEvent(TimelineEventDraft(title = "A")).event()
		val b = service.createEvent(TimelineEventDraft(title = "B")).event()
		val calendar = CalendarDef(CalendarPreset.GREGORIAN)

		service.switchToDated(TimelineDef.MAIN_ID, calendar, EvenSpacing(unit = "day", step = 7, origin = date("year" to 1920, "month" to 12, "day" to 30)))

		assertEquals(date("year" to 1920, "month" to 12, "day" to 30), stored(a.id).start)
		assertEquals(date("year" to 1921, "month" to 1, "day" to 6), stored(b.id).start)
		assertEquals(TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = calendar), service.definition(TimelineDef.MAIN_ID))
	}

	@Test
	fun `switchToManual freezes the dated order and keeps the dates`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()
		val late = service.createEvent(TimelineEventDraft(title = "Late", start = date("year" to 9))).event()
		val early = service.createEvent(TimelineEventDraft(title = "Early", start = date("year" to 1))).event()
		assertEquals(TimeLineEventError.MOVE_NOT_ALLOWED, service.moveEvent(late, toIndex = 1, after = true).error())

		assertEquals(TimelineResult.Ok(null), service.switchToManual(TimelineDef.MAIN_ID))

		assertEquals(0, stored(early.id).order)
		assertEquals(1, stored(late.id).order)
		assertEquals(date("year" to 9), stored(late.id).start)
		val view = service.timelineFlow(TimelineDef.MAIN_ID).first()
		assertEquals(TimelineMode.MANUAL, view.def.mode)
		assertEquals(CalendarDef(), view.def.calendar)
		assertEquals(listOf(early.id, late.id), view.events.map { it.id })
	}

	@Test
	fun `switchToManual writes the dates of an unlabelled event as its label`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory(CalendarDef(epochName = "Landing"))
		val span = service.createEvent(
			TimelineEventDraft(title = "Siege", start = date("year" to -3, "day" to 4), end = date("year" to -3, "day" to 9))
		).event()
		val point = service.createEvent(TimelineEventDraft(title = "Storm", start = date("year" to 2))).event()
		val labelled = service.createEvent(TimelineEventDraft(title = "Feast", label = "Midsummer", start = date("year" to 5))).event()

		assertEquals(TimelineResult.Ok(null), service.switchToManual(TimelineDef.MAIN_ID))

		assertEquals("Year 3 before Landing · Day 4 → Year 3 before Landing · Day 9", stored(span.id).label)
		assertEquals(date("year" to -3, "day" to 4), stored(span.id).start, "Dates stay")
		assertEquals("Year 2", stored(point.id).label)
		assertEquals("Midsummer", stored(labelled.id).label)
		assertEquals(listOf(span.id, point.id, labelled.id), service.timelineFlow(TimelineDef.MAIN_ID).first().events.map { it.id })
	}

	@Test
	fun `switchToManual writes labels in the words it is given`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory(CalendarDef(CalendarPreset.GREGORIAN))
		val event = service.createEvent(
			TimelineEventDraft(title = "Ball", start = date("year" to 1811, "month" to 11, "day" to 26))
		).event()
		val french = object : StoryDateWords by StoryDateWords.English {
			override fun month(month: Int): String = "novembre"
		}

		service.switchToManual(TimelineDef.MAIN_ID, french)

		assertEquals("26 novembre 1811", stored(event.id).label)
	}

	@Test
	fun `switchPreset clears dates the new calendar cannot hold and keeps them as labels`() =
		runTest(mainTestDispatcher) {
			val service = createService()
			service.datedStory()
			val lost = service.createEvent(TimelineEventDraft(start = date("year" to 97, "day" to 210))).event()
			val kept = service.createEvent(TimelineEventDraft(label = "Spring", start = date("year" to 3))).event()
			val labelled = service.createEvent(
				TimelineEventDraft(label = "Morning", start = date("year" to 1, "day" to 5))
			).event()
			val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

			assertEquals(2, service.countDatesLostBySwitch(TimelineDef.MAIN_ID, gregorian))
			assertEquals(TimelineResult.Ok(null), service.switchPreset(TimelineDef.MAIN_ID, gregorian))

			assertEquals(lost.copy(start = null, label = "Year 97 · Day 210"), stored(lost.id))
			assertEquals(kept, stored(kept.id))
			assertEquals(labelled.copy(start = null), stored(labelled.id))
			assertEquals(gregorian, service.definition(TimelineDef.MAIN_ID)?.calendar)
		}

	@Test
	fun `resolveSubject summarises a scene with its chapter and people first`() = runTest(mainTestDispatcher) {
		val service = createService()
		sceneMetadataRepository.storeMetadata(
			SceneMetadata(confirmedReferences = setOf(TEAPOT, WONDERLAND, BOB, ALICE, 999)),
			sceneId = 4,
		)
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()

		val summary = assertIs<SubjectSummary.Scene>(service.resolveSubject(placed))

		assertEquals(4, summary.scene.id)
		assertEquals("Chapter ID 2", summary.chapterPath)
		assertEquals("Content of scene id 4", summary.excerpt)
		assertEquals(listOf(ALICE, BOB, WONDERLAND, TEAPOT), summary.cast.map { it.id })
	}

	@Test
	fun `resolveSubject summarises an entry and reports missing subjects`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeEntry(TEA_PARTY, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val orphan = timeLineRepository.createEvent(TimelineEventDraft(kind = TimelineKind.SCENE, subject = 999))
		val plain = service.createEvent(TimelineEventDraft(title = "Storm")).event()

		val summary = assertIs<SubjectSummary.Entry>(service.resolveSubject(placed))
		assertEquals(TEA_PARTY, summary.entry.id)
		assertEquals(77, summary.primaryImageId)
		assertEquals("The Hatter pours tea.", summary.excerpt)
		assertEquals(SubjectSummary.Missing, service.resolveSubject(orphan))
		assertNull(service.resolveSubject(plain))
	}

	@Test
	fun `displayTitle falls back from subject to title to content to cached title`() = runTest(mainTestDispatcher) {
		val service = createService()
		val scene = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val event = TimeLineEvent(id = 50, order = 0, title = "Title", content = "\n# First line\nSecond", cachedTitle = "Cached")

		assertEquals("Scene ID 4", service.displayTitle(scene.copy(cachedTitle = "Old name")))
		assertEquals("Title", service.displayTitle(event))
		assertEquals("First line", service.displayTitle(event.copy(title = " ")))
		assertEquals("Cached", service.displayTitle(event.copy(title = null, content = "")))
		assertEquals(
			"Gone",
			service.displayTitle(TimeLineEvent(id = 51, order = 0, kind = TimelineKind.SCENE, subject = 999, cachedTitle = "Gone")),
		)
		assertEquals("", service.displayTitle(event.copy(title = null, content = "", cachedTitle = null)))
	}

	@Test
	fun `a manual timeline without a calendar takes any well formed date`() = runTest(mainTestDispatcher) {
		val service = createService()

		val created = service.createEvent(TimelineEventDraft(start = date("year" to 2024, "month" to 5))).event()

		assertEquals(date("year" to 2024, "month" to 5), stored(created.id).start)
		assertEquals(
			TimeLineEventError.DATE_REQUIRED,
			service.createEvent(TimelineEventDraft(end = date("year" to 2024))).error(),
		)
	}

	@Test
	fun `a manual timeline with a calendar validates dates against it`() = runTest(mainTestDispatcher) {
		val service = createService()
		assertEquals(
			TimelineResult.Ok(null),
			service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = CalendarDef(CalendarPreset.GREGORIAN)) },
		)

		assertEquals(TimeLineEventError.DATE_INVALID, service.createEvent(TimelineEventDraft(start = date("hour" to 30))).error())
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.createEvent(TimelineEventDraft(start = date("year" to 2024, "month" to 5, "day" to 1, "hour" to 30))).error(),
		)
	}

	@Test
	fun `switchToDated spaces by the coarsest unit of a story told in hours`() = runTest(mainTestDispatcher) {
		val service = createService()
		val a = service.createEvent(TimelineEventDraft(title = "A")).event()
		val b = service.createEvent(TimelineEventDraft(title = "B")).event()
		val c = service.createEvent(TimelineEventDraft(title = "C")).event()

		assertEquals(TimelineResult.Ok(null), service.switchToDated(TimelineDef.MAIN_ID, CalendarDef(coarsestUnit = "hour")))

		assertEquals(date("hour" to 0), stored(a.id).start)
		assertEquals(date("hour" to 1), stored(b.id).start)
		assertEquals(date("hour" to 2), stored(c.id).start)
	}

	@Test
	fun `displayTitles resolves a mixed list in one call`() = runTest(mainTestDispatcher) {
		val service = createService()
		val scene = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val entry = service.placeEntry(TEA_PARTY, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val titled = TimeLineEvent(id = 60, order = 0, title = "Storm")
		val contentOnly = TimeLineEvent(id = 61, order = 0, content = "# Calm\nAfter")
		val orphan = TimeLineEvent(id = 62, order = 0, kind = TimelineKind.ENTRY, subject = 999, cachedTitle = "Gone")

		val titles = service.displayTitles(listOf(scene, entry, titled, contentOnly, orphan))

		assertEquals(
			mapOf(
				scene.id to "Scene ID 4",
				entry.id to "Tea Party",
				titled.id to "Storm",
				contentOnly.id to "Calm",
				orphan.id to "Gone",
			),
			titles,
		)
	}

	@Test
	fun `displayTitles reads the entry list once for many entry placements`() = runTest(mainTestDispatcher) {
		lateinit var spy: EncyclopediaRepository
		val service = createService { real -> spyk(real).also { spy = it } }
		val placements = listOf(TEA_PARTY, ALICE, BOB, TEAPOT).mapIndexed { index, subject ->
			TimeLineEvent(id = 70 + index, order = index, kind = TimelineKind.ENTRY, subject = subject)
		}

		val titles = service.displayTitles(placements)

		assertEquals(listOf("Tea Party", "Alice", "Bob", "Teapot"), placements.map { titles.getValue(it.id) })
		coVerify(atMost = 1) { spy.readEntryDefs() }
		coVerify(exactly = 0) { spy.findEntryDef(any()) }
	}

	@Test
	fun `updateDefinition writes settings through project data and the flow follows`() = runTest(mainTestDispatcher) {
		val service = createService()
		assertEquals(TimelineDef.DEFAULT, service.timelineFlow(TimelineDef.MAIN_ID).first().def)
		val calendar = CalendarDef(epochName = "The Fall", coarsestUnit = "hour")

		service.datedStory(calendar)

		val expected = TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = calendar)
		assertEquals(expected, service.timelineFlow(TimelineDef.MAIN_ID).first { it.def.mode == TimelineMode.DATED }.def)
		assertEquals(listOf(expected), ProjectDataDatasource(ffs, toml, projDef).load().data.timelines)
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = CalendarDef(coarsestUnit = "fortnight")) }.error(),
		)
		assertEquals(listOf(expected), ProjectDataDatasource(ffs, toml, projDef).load().data.timelines)
	}

	@Test
	fun `lanes lists the lane order first then the rest alphabetically`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(laneOrder = listOf("Queen", "Alice")) }
		service.createEvent(TimelineEventDraft(lane = "rabbit"))
		service.createEvent(TimelineEventDraft(lane = "Alice"))
		service.createEvent(TimelineEventDraft(lane = "Hatter"))
		service.createEvent(TimelineEventDraft(lane = "Hatter"))

		assertEquals(listOf("Queen", "Alice", "Hatter", "rabbit"), service.lanes(TimelineDef.MAIN_ID))
	}

	@Test
	fun `subjectUpdates follows only the placed subject`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val plain = service.createEvent(TimelineEventDraft(title = "Storm")).event()
		val updates = mutableListOf<PlacedSubject>()
		val plainUpdates = mutableListOf<PlacedSubject>()
		val collectors = listOf(
			launch { service.subjectUpdates(placed.id).toList(updates) },
			launch { service.subjectUpdates(plain.id).toList(plainUpdates) },
		)
		advanceUntilIdle()

		sceneRepository.renameScene(sceneRepository.getSceneItemFromId(6)!!, "Elsewhere")
		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(ALICE)), sceneId = 5)
		val alice = encyclopediaRepository.findEntryDef(ALICE)!!
		encyclopediaRepository.updateEntry(
			alice,
			name = alice.name,
			text = "Older now.",
			tags = emptySet(),
			excludeFromDictionary = false,
			images = emptyList(),
		)
		advanceUntilIdle()
		assertEquals(listOf("Scene ID 4"), updates.map { it.displayTitle })

		sceneRepository.renameScene(sceneRepository.getSceneItemFromId(4)!!, "Down the Hole")
		advanceUntilIdle()

		val update = updates.last()
		assertEquals(2, updates.size)
		assertEquals("Down the Hole", assertIs<SubjectSummary.Scene>(update.summary).scene.name)
		assertEquals("Down the Hole", update.displayTitle)
		assertEquals(emptyList(), plainUpdates)
		collectors.forEach { it.cancel() }
	}

	@Test
	fun `subjectUpdates starts with the current subject`() = runTest(mainTestDispatcher) {
		val service = createService()
		val scenePlacement = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val entryPlacement = service.placeEntry(TEA_PARTY, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val orphan = timeLineRepository.createEvent(TimelineEventDraft(kind = TimelineKind.ENTRY, subject = 999, title = "Gone"))

		val scene = service.subjectUpdates(scenePlacement.id).first()
		val entry = service.subjectUpdates(entryPlacement.id).first()
		val missing = service.subjectUpdates(orphan.id).first()

		assertEquals("Chapter ID 2", assertIs<SubjectSummary.Scene>(scene.summary).chapterPath)
		assertEquals("Scene ID 4", scene.displayTitle)
		assertEquals("The Hatter pours tea.", assertIs<SubjectSummary.Entry>(entry.summary).excerpt)
		assertEquals("Tea Party", entry.displayTitle)
		assertEquals(PlacedSubject(SubjectSummary.Missing, "Gone"), missing)
	}

	@Test
	fun `subjectUpdates emits a change that lands right after the first subject`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val updates = mutableListOf<PlacedSubject>()
		val collector = launch {
			service.subjectUpdates(placed.id).collect { update ->
				updates += update
				if (updates.size == 1) {
					sceneRepository.renameScene(sceneRepository.getSceneItemFromId(4)!!, "Down the Hole")
				}
			}
		}
		advanceUntilIdle()

		assertEquals(listOf("Scene ID 4", "Down the Hole"), updates.map { it.displayTitle })
		collector.cancel()
	}

	@Test
	fun `deleteEvent removes a placement and leaves its scene alone`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()

		assertEquals(TimelineResult.Ok(null), service.deleteEvent(placed.id))

		assertEquals(emptyList(), timeLineRepository.currentEvents())
		assertEquals("Scene ID 4", sceneRepository.getSceneItemFromId(4)?.name)
		assertEquals(TimeLineEventError.EVENT_NOT_FOUND, service.deleteEvent(placed.id).error())
	}


	// region Multiple timelines

	@Test
	fun `a new timeline is trimmed and listed after main`() = runTest(mainTestDispatcher) {
		val service = createService()

		val created = service.createTimeline("  Backstory ").def()

		assertEquals(TimelineDef(id = created.id, name = "Backstory"), created)
		assertEquals(listOf(TimelineDef.DEFAULT, created), service.timelines.first())
		assertEquals(created, service.definition(created.id))
		assertEquals(listOf(created), storedData().timelines)
	}

	@Test
	fun `a timeline name is non-blank short and unique ignoring case`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()

		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, service.createTimeline("   ").error())
		assertEquals(
			TimeLineEventError.TIMELINE_NAME_INVALID,
			service.createTimeline("x".repeat(TimelineLimits.MAX_TIMELINE_NAME + 1)).error(),
		)
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, service.createTimeline("BACKSTORY").error())
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, service.createTimeline("main").error())
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.createTimeline("Dated", TimelineMode.DATED).error())
		service.createTimeline("x".repeat(TimelineLimits.MAX_TIMELINE_NAME)).def()

		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, service.renameTimeline(backstory.id, " Main ").error())
		assertEquals("BackStory", service.renameTimeline(backstory.id, "BackStory").def().name)
		assertEquals("Later", service.renameTimeline(backstory.id, " Later ").def().name)
		assertEquals("Later", service.definition(backstory.id)?.name)
		assertEquals(TimeLineEventError.TIMELINE_NOT_FOUND, service.renameTimeline("gone0001", "Gone").error())
	}

	@Test
	fun `a stored timeline whose name or id breaks the rules can still be edited`() = runTest(mainTestDispatcher) {
		val blank = TimelineDef(id = "aaaa0001", name = "  ")
		val long = TimelineDef(id = "odd.id!", name = "x".repeat(TimelineLimits.MAX_TIMELINE_NAME + 1))
		storeTimelines(blank, long)
		val service = createService()
		val calendar = CalendarDef(epochName = "Landing")

		assertEquals(TimelineResult.Ok(null), service.updateDefinition(blank.id) { it.copy(laneOrder = listOf("North")) })
		assertEquals(TimelineResult.Ok(null), service.switchToDated(long.id, calendar))

		assertEquals(blank.copy(laneOrder = listOf("North")), service.definition(blank.id))
		assertEquals(long.copy(mode = TimelineMode.DATED, calendar = calendar), service.definition(long.id))
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, service.renameTimeline(blank.id, " ").error())
		assertEquals(
			TimeLineEventError.TIMELINE_NAME_INVALID,
			service.renameTimeline(long.id, "y".repeat(TimelineLimits.MAX_TIMELINE_NAME + 1)).error(),
		)
		assertEquals("Fixed", service.renameTimeline(blank.id, " Fixed ").def().name)
	}

	@Test
	fun `a switch the definition rules refuse leaves every event as it was`() = runTest(mainTestDispatcher) {
		val backstory = TimelineDef(
			id = "aaaa0001",
			name = "Backstory",
			mode = TimelineMode.DATED,
			calendar = CalendarDef(),
			laneOrder = listOf(" "),
		)
		storeTimelines(backstory)
		val service = createService()
		val event = service.createEvent(
			TimelineEventDraft(title = "Old", start = date("year" to 97, "day" to 210), timeline = backstory.id)
		).event()
		val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

		assertEquals(TimeLineEventError.LANE_INVALID, service.switchToDated(backstory.id, gregorian).error())
		assertEquals(TimeLineEventError.LANE_INVALID, service.switchPreset(backstory.id, gregorian).error())
		assertEquals(TimeLineEventError.LANE_INVALID, service.switchToManual(backstory.id).error())

		assertEquals(event, stored(event.id))
		assertEquals(backstory, service.definition(backstory.id))
	}

	@Test
	fun `deleting a timeline deletes its events and keeps its id as deleted`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val kept = service.createEvent(TimelineEventDraft(title = "Main")).event()
		val first = service.createEvent(TimelineEventDraft(title = "A", timeline = backstory.id)).event()
		val placed = service.placeScene(4, null, null, null, false, timeline = backstory.id).event()

		assertEquals(TimeLineEventError.MAIN_NOT_DELETABLE, service.deleteTimeline(TimelineDef.MAIN_ID).error())
		val deleted = assertIs<TimelineDefResult.Ok>(service.deleteTimeline(backstory.id))

		assertEquals(TimelineDefResult.Ok(backstory, deletedEvents = 2), deleted)
		assertEquals(listOf(kept.id), timeLineRepository.currentEvents().map { it.id })
		coVerify { syncJournal.recordIdDeletion(first.id) }
		coVerify { syncJournal.recordIdDeletion(placed.id) }
		coVerify(exactly = 0) { syncJournal.recordIdDeletion(kept.id) }
		assertEquals(emptyList(), storedData().timelines)
		assertEquals(setOf(backstory.id), storedData().deletedTimelines)
		assertEquals(listOf(TimelineDef.DEFAULT), service.timelines.first())
		assertNull(service.definition(backstory.id))
		assertEquals(TimeLineEventError.TIMELINE_NOT_FOUND, service.deleteTimeline(backstory.id).error())
		assertEquals(TimeLineEventError.TIMELINE_NOT_FOUND, service.renameTimeline(backstory.id, "Back").error())
	}

	@Test
	fun `a new timeline id is never main a live id or a deleted one`() = runTest(mainTestDispatcher) {
		val service = createService()
		val draws = ArrayDeque(listOf("main", "aaaa0001", "aaaa0001", "aaaa0002", "aaaa0001", "aaaa0002", "aaaa0003"))
		service.nextTimelineId = { draws.removeFirst() }

		val first = service.createTimeline("First").def()
		val second = service.createTimeline("Second").def()
		service.deleteTimeline(first.id).def()
		val third = service.createTimeline("Third").def()

		assertEquals(listOf("aaaa0001", "aaaa0002", "aaaa0003"), listOf(first.id, second.id, third.id))
		assertEquals(emptyList(), draws.toList())
	}

	@Test
	fun `a new timeline takes its lane order in the same write, or not at all`() = runTest(mainTestDispatcher) {
		val service = createService()

		assertEquals(
			TimelineDefResult.Invalid(TimeLineEventError.LANE_INVALID),
			service.createTimeline("Backstory", laneOrder = listOf("Keeper", " ")),
		)
		assertEquals(listOf(TimelineDef.MAIN_ID), service.timelines.first().map { it.id })

		val created = service.createTimeline("Backstory", laneOrder = listOf("Keeper", "Village")).def()
		assertEquals(listOf("Keeper", "Village"), service.definition(created.id)?.laneOrder)
	}

	@Test
	fun `events on a timeline that is not live are stragglers until deleted`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val gone = service.createTimeline("Gone").def()
		service.deleteTimeline(gone.id).def()
		val main = service.createEvent(TimelineEventDraft(title = "Main")).event()
		val live = service.createEvent(TimelineEventDraft(title = "Live", timeline = backstory.id)).event()
		// As sync stores an event created offline on another device.
		val late = timeLineRepository.createEvent(TimelineEventDraft(title = "Late", timeline = gone.id))
		val unknown = timeLineRepository.createEvent(TimelineEventDraft(title = "Unknown", timeline = "zzzz9999"))

		assertEquals(listOf(late.id, unknown.id), service.orphansFlow.first().map { it.id })
		assertEquals(listOf(main.id), service.timelineFlow(TimelineDef.MAIN_ID).first().events.map { it.id })
		assertEquals(listOf(live.id), service.timelineFlow(backstory.id).first().events.map { it.id })
		assertEquals(
			TimeLineEventError.TIMELINE_NOT_FOUND,
			service.createEvent(TimelineEventDraft(title = "No", timeline = gone.id)).error(),
		)

		assertEquals(2, service.deleteOrphans())

		assertEquals(emptyList(), service.orphansFlow.first())
		assertEquals(setOf(main.id, live.id), timeLineRepository.currentEvents().mapTo(HashSet()) { it.id })
		coVerify { syncJournal.recordIdDeletion(late.id) }
		assertEquals(0, service.deleteOrphans())
	}

	@Test
	fun `stragglers move together, appended in order, leaving a placement already there`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.datedTimeline("Backstory", CalendarDef(CalendarPreset.GREGORIAN))
		val onBackstory = service.createEvent(
			TimelineEventDraft(title = "Before", start = date("year" to 1900), timeline = backstory.id)
		).event()
		service.placeScene(4, date("year" to 1901), null, null, false, timeline = backstory.id).event()
		// As sync stores events made offline on a timeline deleted since.
		val letter = timeLineRepository.createEvent(TimelineEventDraft(title = "Letter", timeline = "zzzz9999"))
		val scene = timeLineRepository.createEvent(
			TimelineEventDraft(kind = TimelineKind.SCENE, subject = 4, timeline = "zzzz9999")
		)
		val dated = timeLineRepository.createEvent(
			TimelineEventDraft(title = "Dated", start = date("year" to 1923), timeline = "zzzz9999")
		)

		assertEquals(2, service.moveOrphans(backstory.id))

		assertEquals(listOf(scene.id), service.orphansFlow.first().map { it.id })
		assertEquals(backstory.id, stored(letter.id).timeline)
		assertNull(stored(letter.id).start)
		assertEquals(date("year" to 1923), stored(dated.id).start)
		assertEquals(
			listOf(onBackstory.order + 2, onBackstory.order + 3),
			listOf(stored(letter.id).order, stored(dated.id).order),
		)
		assertEquals(0, service.moveOrphans("nope"))
	}

	@Test
	fun `a straggler can still be edited`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory(CalendarDef(CalendarPreset.GREGORIAN))
		val straggler = timeLineRepository.createEvent(
			TimelineEventDraft(title = "Lost", timeline = "zzzz9999", start = date("year" to 97, "day" to 210))
		)

		service.setLabel(straggler.id, "Long ago").event()
		service.updateEvent(stored(straggler.id).copy(title = "Found", lane = "Alice")).event()
		service.setDates(straggler.id, start = null, end = null).event()

		assertEquals(
			straggler.copy(title = "Found", label = "Long ago", lane = "Alice", start = null),
			stored(straggler.id),
		)
	}

	@Test
	fun `a subject can be placed once on each timeline`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()

		val onMain = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		val onBackstory = service.placeScene(4, null, null, null, false, timeline = backstory.id).event()
		service.placeEntry(TEA_PARTY, null, null, null, false, timeline = backstory.id).event()

		assertNull(onMain.timeline)
		assertEquals(backstory.id, onBackstory.timeline)
		assertEquals(
			TimeLineEventError.DUPLICATE_PLACEMENT,
			service.placeScene(4, null, null, null, false, timeline = backstory.id).error(),
		)
		assertEquals(
			TimeLineEventError.DUPLICATE_PLACEMENT,
			service.placeEntry(TEA_PARTY, null, null, null, false, timeline = backstory.id).error(),
		)
		assertEquals(
			TimeLineEventError.TIMELINE_NOT_FOUND,
			service.placeScene(5, null, null, null, false, timeline = "zzzz9999").error(),
		)
		assertEquals(3, timeLineRepository.currentEvents().size)
	}

	@Test
	fun `a subject's placements list live timelines in order and then stragglers`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val onBackstory = service.placeScene(4, null, null, null, false, timeline = backstory.id).event()
		val onMain = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		service.placeScene(5, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		service.placeEntry(TEA_PARTY, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		// As sync stores a placement made offline on a timeline deleted since.
		val straggler = timeLineRepository.createEvent(
			TimelineEventDraft(kind = TimelineKind.SCENE, subject = 4, timeline = "zzzz9999")
		)

		val placements = service.placementsFlow(TimelineKind.SCENE, 4).first()

		assertEquals(listOf(onMain.id, onBackstory.id, straggler.id), placements.map { it.event.id })
		assertEquals(listOf(TimelineDef.MAIN_ID, backstory.id, null), placements.map { it.def?.id })
		assertEquals(emptyList(), service.placementsFlow(TimelineKind.ENTRY, 4).first())
	}

	@Test
	fun `a draft may spell main out and is stored as main`() = runTest(mainTestDispatcher) {
		val service = createService()

		val created = service.createEvent(TimelineEventDraft(title = "Storm", timeline = TimelineDef.MAIN_ID)).event()

		assertNull(stored(created.id).timeline)
		assertEquals(
			listOf(created.id),
			service.timelineFlow(TimelineDef.MAIN_ID).first().events.map { it.id },
		)
	}

	@Test
	fun `each event follows the date rules of its own timeline`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.datedTimeline("Backstory", CalendarDef(CalendarPreset.GREGORIAN))
		val monthly = date("year" to 1923, "month" to 11)

		val onMain = service.createEvent(TimelineEventDraft(title = "Undated")).event()
		assertEquals(
			TimeLineEventError.DATE_REQUIRED,
			service.createEvent(TimelineEventDraft(title = "Undated", timeline = backstory.id)).error(),
		)
		val onBackstory = service.createEvent(TimelineEventDraft(start = monthly, timeline = backstory.id)).event()
		assertEquals(
			TimeLineEventError.DATE_REQUIRED,
			service.placeScene(4, null, null, null, false, timeline = backstory.id).error(),
		)

		service.datedStory()
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.createEvent(TimelineEventDraft(start = monthly)).error(),
		)
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.setLabel(onMain.id, "Later").error())
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.setDates(onBackstory.id, null, null).error())
		service.setDates(onBackstory.id, date("year" to 1923, "month" to 12), null).event()
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.updateEvent(stored(onBackstory.id).copy(start = date("year" to 1, "day" to 400))).error(),
		)
	}

	@Test
	fun `updateEvent cannot move an event to another timeline`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val created = service.createEvent(TimelineEventDraft(title = "Storm")).event()

		service.updateEvent(stored(created.id).copy(title = "Gale", timeline = backstory.id)).event()

		assertEquals("Gale", stored(created.id).title)
		assertNull(stored(created.id).timeline)
	}

	@Test
	fun `a drag is refused on a dated timeline while main is manual`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.datedTimeline("Backstory")
		val late = service.createEvent(TimelineEventDraft(start = date("year" to 9), timeline = backstory.id)).event()
		service.createEvent(TimelineEventDraft(start = date("year" to 1), timeline = backstory.id)).event()
		val first = service.createEvent(TimelineEventDraft(title = "First")).event()
		service.createEvent(TimelineEventDraft(title = "Second")).event()

		assertEquals(TimeLineEventError.MOVE_NOT_ALLOWED, service.moveEvent(late, toIndex = 0, after = false).error())
		service.moveEvent(first, toIndex = 1, after = true).event()
	}

	@Test
	fun `a mode or calendar switch on one timeline leaves main untouched`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		service.createEvent(TimelineEventDraft(title = "Main undated")).event()
		service.createEvent(TimelineEventDraft(title = "Main old", start = date("year" to 97, "day" to 210))).event()
		val a = service.createEvent(TimelineEventDraft(title = "A", timeline = backstory.id)).event()
		val b = service.createEvent(TimelineEventDraft(title = "B", timeline = backstory.id)).event()
		val mainBefore = timeLineRepository.currentEvents().filter { it.timeline == null }
		val gregorian = CalendarDef(CalendarPreset.GREGORIAN)

		assertEquals(TimelineResult.Ok(null), service.switchToDated(backstory.id, CalendarDef()))
		assertEquals(date("year" to 0), stored(a.id).start)
		assertEquals(date("year" to 1), stored(b.id).start)
		assertEquals(0, service.countDatesLostBySwitch(backstory.id, gregorian))
		assertEquals(1, service.countDatesLostBySwitch(TimelineDef.MAIN_ID, gregorian))

		assertEquals(TimelineResult.Ok(null), service.switchPreset(backstory.id, gregorian))
		assertEquals(TimelineResult.Ok(null), service.switchToManual(backstory.id))

		assertEquals(TimelineDef(backstory.id, "Backstory", TimelineMode.MANUAL, gregorian), service.definition(backstory.id))
		assertEquals(TimelineDef.DEFAULT, service.definition(TimelineDef.MAIN_ID))
		assertEquals(mainBefore, timeLineRepository.currentEvents().filter { it.timeline == null })
		assertEquals(
			TimeLineEventError.TIMELINE_NOT_FOUND,
			service.switchToDated("zzzz9999", CalendarDef()).error(),
		)
	}

	@Test
	fun `lanes and the flow are scoped to one timeline`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		service.updateDefinition(TimelineDef.MAIN_ID) { it.copy(laneOrder = listOf("Queen")) }
		service.updateDefinition(backstory.id) { it.copy(laneOrder = listOf("Hatter")) }
		service.createEvent(TimelineEventDraft(lane = "Alice"))
		val rabbit = service.createEvent(TimelineEventDraft(lane = "rabbit", timeline = backstory.id)).event()

		assertEquals(listOf("Queen", "Alice"), service.lanes(TimelineDef.MAIN_ID))
		assertEquals(listOf("Hatter", "rabbit"), service.lanes(backstory.id))
		val view = service.timelineFlow(backstory.id).first()
		assertEquals(listOf("Hatter"), view.def.laneOrder)
		assertEquals(listOf(rabbit.id), view.events.map { it.id })
		assertEquals(listOf("Queen"), service.definition(TimelineDef.MAIN_ID)?.laneOrder)
		assertEquals(
			TimeLineEventError.TIMELINE_NOT_FOUND,
			service.updateDefinition("zzzz9999") { it.copy(laneOrder = listOf("X")) }.error(),
		)
	}

	@Test
	fun `a move needs another live timeline that does not hold the subject`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val event = service.createEvent(TimelineEventDraft(title = "Storm")).event()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()
		service.placeScene(4, null, null, null, false, timeline = backstory.id).event()

		assertEquals(TimeLineEventError.SAME_TIMELINE, service.moveToTimeline(event.id, TimelineDef.MAIN_ID).error())
		assertEquals(TimeLineEventError.TIMELINE_NOT_FOUND, service.moveToTimeline(event.id, "zzzz9999").error())
		assertEquals(TimeLineEventError.EVENT_NOT_FOUND, service.moveToTimeline(999, backstory.id).error())
		assertEquals(TimeLineEventError.DUPLICATE_PLACEMENT, service.moveToTimeline(placed.id, backstory.id).error())
		assertNull(stored(placed.id).timeline)
	}

	@Test
	fun `a move appends at the target and keeps the rest of the event`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		service.createEvent(TimelineEventDraft(title = "A", timeline = backstory.id)).event()
		service.createEvent(TimelineEventDraft(title = "B", timeline = backstory.id)).event()
		val event = service.createEvent(
			TimelineEventDraft(
				title = "Storm",
				content = "Rain.",
				tags = setOf("weather"),
				references = setOf(ALICE),
				lane = "Alice",
				milestone = true,
			)
		).event()
		every { syncJournal.isServerSynchronized() } returns true

		val moved = service.moveToTimeline(event.id, backstory.id).event()

		assertEquals(event.copy(timeline = backstory.id, order = 2), moved)
		assertEquals(moved, stored(event.id))
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(event.id) }
		assertEquals(emptyList(), service.timelineFlow(TimelineDef.MAIN_ID).first().events)
		assertEquals(event.id, service.timelineFlow(backstory.id).first().events.last().id)

		service.moveToTimeline(event.id, TimelineDef.MAIN_ID).event()
		assertNull(stored(event.id).timeline)
	}

	@Test
	fun `a move to a dated timeline keeps a valid start and drops an end that no longer fits`() =
		runTest(mainTestDispatcher) {
			val service = createService()
			val backstory = service.datedTimeline("Backstory", CalendarDef(CalendarPreset.GREGORIAN))
			val start = date("year" to 1923, "month" to 11, "day" to 14)
			val early = service.createEvent(
				TimelineEventDraft(title = "Early", start = start, end = date("year" to 1923, "month" to 11, "day" to 10))
			).event()
			val span = service.createEvent(
				TimelineEventDraft(title = "Span", start = start, end = date("year" to 1923, "month" to 11, "day" to 15))
			).event()

			service.moveToTimeline(early.id, backstory.id).event()
			service.moveToTimeline(span.id, backstory.id).event()

			assertEquals(start to null, stored(early.id).start to stored(early.id).end)
			assertEquals(date("year" to 1923, "month" to 11, "day" to 15), stored(span.id).end)
			assertNull(stored(early.id).label, "A dated target needs no label")
		}

	@Test
	fun `a move to a dated timeline needs a start that fits unless one is given or undated is allowed`() =
		runTest(mainTestDispatcher) {
			val service = createService()
			val backstory = service.datedTimeline("Backstory", CalendarDef(CalendarPreset.GREGORIAN))
			val undated = service.createEvent(TimelineEventDraft(title = "Undated")).event()
			val story = service.createEvent(
				TimelineEventDraft(title = "Story", start = date("year" to 97, "day" to 210), end = date("year" to 98))
			).event()
			val given = date("year" to 1923, "month" to 11, "day" to 11)

			assertEquals(TimeLineEventError.DATE_REQUIRED, service.moveToTimeline(undated.id, backstory.id).error())
			assertEquals(TimeLineEventError.DATE_INVALID, service.moveToTimeline(story.id, backstory.id).error())
			assertEquals(
				TimeLineEventError.DATE_INVALID,
				service.moveToTimeline(story.id, backstory.id, start = date("year" to 1, "day" to 400)).error(),
			)
			assertEquals(
				TimeLineEventError.END_BEFORE_START,
				service.moveToTimeline(story.id, backstory.id, start = given, end = date("year" to 1900)).error(),
			)
			assertEquals(2, timeLineRepository.currentEvents().count { it.timeline == null })

			service.moveToTimeline(story.id, backstory.id, start = given).event()
			assertEquals(given to null, stored(story.id).start to stored(story.id).end)

			service.moveToTimeline(undated.id, backstory.id, allowUndated = true).event()
			assertEquals(backstory.id, stored(undated.id).timeline)
			assertNull(stored(undated.id).start)
		}

	@Test
	fun `an allowed undated move drops a start the target cannot hold`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.datedTimeline("Backstory", CalendarDef(CalendarPreset.GREGORIAN))
		val story = service.createEvent(
			TimelineEventDraft(title = "Story", start = date("year" to 97, "day" to 210), end = date("year" to 98))
		).event()

		service.moveToTimeline(story.id, backstory.id, allowUndated = true).event()

		assertEquals(null to null, stored(story.id).start to stored(story.id).end)
	}

	@Test
	fun `a move to a manual timeline writes the dates into a blank label`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()
		val loose = service.createTimeline("Loose").def()
		val strict = service.createTimeline("Strict", calendar = CalendarDef(CalendarPreset.GREGORIAN)).def()
		val point = service.createEvent(TimelineEventDraft(title = "Storm", start = date("year" to 2))).event()
		val labelled = service.createEvent(
			TimelineEventDraft(title = "Feast", label = "Midsummer", start = date("year" to 5))
		).event()
		val old = service.createEvent(TimelineEventDraft(title = "Old", start = date("year" to 97, "day" to 210))).event()

		service.moveToTimeline(point.id, loose.id).event()
		service.moveToTimeline(labelled.id, loose.id).event()
		service.moveToTimeline(old.id, strict.id).event()

		assertEquals("Year 2" to date("year" to 2), stored(point.id).label to stored(point.id).start)
		assertEquals("Midsummer", stored(labelled.id).label)
		assertEquals("Year 97 · Day 210" to null, stored(old.id).label to stored(old.id).start)
	}

	@Test
	fun `a move with an edit that does not validate changes nothing`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val event = service.createEvent(TimelineEventDraft(title = "Storm", lane = "Alice")).event()

		assertEquals(
			TimeLineEventError.LABEL_TOO_LONG,
			service.moveToTimeline(event.id, backstory.id) {
				it.copy(label = "x".repeat(TimelineLimits.MAX_LABEL + 1))
			}.error(),
		)
		assertEquals(
			TimeLineEventError.LANE_INVALID,
			service.moveToTimeline(event.id, backstory.id) {
				it.copy(lane = "x".repeat(TimelineLimits.MAX_LANE + 1))
			}.error(),
		)
		assertEquals(event, stored(event.id))
	}

	@Test
	fun `a move applies its edit to the carried event in the same write`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()
		val loose = service.createTimeline("Loose").def()
		val event = service.createEvent(TimelineEventDraft(title = "Storm", start = date("year" to 2))).event()
		every { syncJournal.isServerSynchronized() } returns true
		var seen: TimeLineEvent? = null

		val moved = service.moveToTimeline(event.id, loose.id) {
			seen = it
			it.copy(title = "Squall", lane = " Alice ", milestone = true, timeline = null, order = 99)
		}.event()

		assertEquals("Year 2", seen?.label, "The edit sees the dates written into the label")
		assertEquals(
			event.copy(title = "Squall", label = "Year 2", lane = "Alice", milestone = true, timeline = loose.id),
			moved,
		)
		assertEquals(moved, stored(event.id))
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(event.id) }
	}

	@Test
	fun `a placement moved with an edit takes only its placement fields`() = runTest(mainTestDispatcher) {
		val service = createService()
		val backstory = service.createTimeline("Backstory").def()
		val placed = service.placeScene(4, null, null, null, false, timeline = TimelineDef.MAIN_ID).event()

		val moved = service.moveToTimeline(placed.id, backstory.id) {
			it.copy(title = "Mine", content = "Words", label = "Dusk", lane = "Alice")
		}.event()

		assertEquals(placed.copy(label = "Dusk", lane = "Alice", timeline = backstory.id), moved)
	}

	// endregion

	private companion object {
		const val ALICE = 50
		const val WONDERLAND = 51
		const val TEA_PARTY = 52
		const val BOB = 53
		const val TEAPOT = 54
	}
}
