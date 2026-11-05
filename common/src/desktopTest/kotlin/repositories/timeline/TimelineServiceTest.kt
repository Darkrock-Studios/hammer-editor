package repositories.timeline

import PROJECT_1_NAME
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

	private suspend fun TimelineService.datedStory(calendar: CalendarDef = CalendarDef()) {
		assertEquals(TimelineResult.Ok(null), updateDefinition { it.copy(mode = TimelineMode.DATED, calendar = calendar) })
	}

	private suspend fun stored(id: Int): TimeLineEvent = timeLineRepository.currentEvents().first { it.id == id }

	@Test
	fun `a subject can be placed only once`() = runTest(mainTestDispatcher) {
		val service = createService()

		val placed = service.placeScene(4, start = null, end = null, lane = null, milestone = false).event()

		assertEquals(TimelineKind.SCENE, placed.kind)
		assertEquals(4, placed.subject)
		assertEquals("Scene ID 4", placed.cachedTitle)
		assertEquals(
			TimeLineEventError.DUPLICATE_PLACEMENT,
			service.placeScene(4, start = null, end = null, lane = null, milestone = false).error(),
		)
	}

	@Test
	fun `an archived scene cannot be placed or referenced`() = runTest(mainTestDispatcher) {
		val service = createService()
		sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)

		assertEquals(TimeLineEventError.SUBJECT_ARCHIVED, service.placeScene(4, null, null, null, false).error())
		assertEquals(
			TimeLineEventError.REFERENCE_NOT_FOUND,
			service.createEvent(TimelineEventDraft(title = "Storm", references = setOf(4))).error(),
		)
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `a placed scene that is archived resolves as archived until it is unarchived`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false).event()

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
			service.placeEntry(ALICE, start = null, end = null, lane = null, milestone = false).error(),
		)
		val placed = service.placeEntry(TEA_PARTY, start = null, end = null, lane = "Hatter", milestone = true).event()
		assertEquals(TimelineKind.ENTRY, placed.kind)
		assertEquals("Tea Party", placed.cachedTitle)
		assertEquals(true, placed.milestone)
	}

	@Test
	fun `placing a missing subject or a chapter fails`() = runTest(mainTestDispatcher) {
		val service = createService()

		assertEquals(TimeLineEventError.SUBJECT_NOT_FOUND, service.placeScene(999, null, null, null, false).error())
		assertEquals(TimeLineEventError.SUBJECT_NOT_FOUND, service.placeEntry(999, null, null, null, false).error())
		assertEquals(TimeLineEventError.SUBJECT_WRONG_TYPE, service.placeScene(2, null, null, null, false).error())
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `a placement takes its label in the same write`() = runTest(mainTestDispatcher) {
		val service = createService()
		val tooLong = "x".repeat(TimelineLimits.MAX_LABEL + 1)

		assertEquals(TimeLineEventError.LABEL_TOO_LONG, service.placeScene(4, null, null, null, false, tooLong).error())
		assertEquals(TimeLineEventError.LABEL_TOO_LONG, service.placeEntry(TEA_PARTY, null, null, null, false, tooLong).error())
		assertEquals(emptyList(), timeLineRepository.currentEvents())

		val placed = service.placeScene(4, null, null, null, false, label = "At dawn").event()
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
		assertEquals(TimeLineEventError.DATE_REQUIRED, service.placeScene(4, null, null, null, false).error())

		val placed = service.placeScene(4, date("year" to 97), null, null, false).event()
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
			service.updateDefinition { it.copy(calendar = null) }.error(),
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

		val placed = service.placeScene(4, null, null, lane = " Queen ", milestone = false).event()
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
			service.placeScene(4, null, null, lane = "F", milestone = false).error(),
		)
		assertEquals(
			TimeLineEventError.TOO_MANY_LANES,
			service.updateEvent(stored(alsoInC.id).copy(lane = "F")).error(),
		)
		assertEquals(TimeLineEventError.TOO_MANY_LANES, service.updateDefinition { it.copy(laneOrder = listOf("F")) }.error())

		// Renaming E's only entry to F trades one lane for another.
		service.setLane(events[4].id, "F").event()
		assertEquals(listOf("A", "B", "C", "D", "F"), service.lanes())
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
		val placed = service.placeScene(4, start = null, end = null, lane = null, milestone = false).event()

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
	fun `switchToDated spaces undated events evenly and derives the order from the dates`() =
		runTest(mainTestDispatcher) {
			val service = createService()
			val a = service.createEvent(TimelineEventDraft(title = "A")).event()
			val b = service.createEvent(TimelineEventDraft(title = "B", start = date("year" to 5))).event()
			val c = service.createEvent(TimelineEventDraft(title = "C")).event()

			assertEquals(TimelineResult.Ok(null), service.switchToDated(CalendarDef(), spacing = null))

			assertEquals(date("year" to 0), stored(a.id).start)
			assertEquals(date("year" to 5), stored(b.id).start)
			assertEquals(date("year" to 2), stored(c.id).start)
			val view = service.timelineFlow.first()
			assertEquals(TimelineMode.DATED, view.def.mode)
			assertEquals(listOf(a.id, c.id, b.id), view.events.map { it.id })
		}

	@Test
	fun `switchToDated uses the given spacing`() = runTest(mainTestDispatcher) {
		val service = createService()
		val a = service.createEvent(TimelineEventDraft(title = "A")).event()
		val b = service.createEvent(TimelineEventDraft(title = "B")).event()
		val calendar = CalendarDef(CalendarPreset.GREGORIAN)

		service.switchToDated(calendar, EvenSpacing(unit = "day", step = 7, origin = date("year" to 1920, "month" to 12, "day" to 30)))

		assertEquals(date("year" to 1920, "month" to 12, "day" to 30), stored(a.id).start)
		assertEquals(date("year" to 1921, "month" to 1, "day" to 6), stored(b.id).start)
		assertEquals(TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = calendar), service.definition())
	}

	@Test
	fun `switchToManual freezes the dated order and keeps the dates`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.datedStory()
		val late = service.createEvent(TimelineEventDraft(title = "Late", start = date("year" to 9))).event()
		val early = service.createEvent(TimelineEventDraft(title = "Early", start = date("year" to 1))).event()
		assertEquals(TimeLineEventError.MOVE_NOT_ALLOWED, service.moveEvent(late, toIndex = 1, after = true).error())

		assertEquals(TimelineResult.Ok(null), service.switchToManual())

		assertEquals(0, stored(early.id).order)
		assertEquals(1, stored(late.id).order)
		assertEquals(date("year" to 9), stored(late.id).start)
		val view = service.timelineFlow.first()
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

		assertEquals(TimelineResult.Ok(null), service.switchToManual())

		assertEquals("Year 3 before Landing · Day 4 → Year 3 before Landing · Day 9", stored(span.id).label)
		assertEquals(date("year" to -3, "day" to 4), stored(span.id).start, "Dates stay")
		assertEquals("Year 2", stored(point.id).label)
		assertEquals("Midsummer", stored(labelled.id).label)
		assertEquals(listOf(span.id, point.id, labelled.id), service.timelineFlow.first().events.map { it.id })
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

		service.switchToManual(french)

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

			assertEquals(2, service.countDatesLostBySwitch(gregorian))
			assertEquals(TimelineResult.Ok(null), service.switchPreset(gregorian))

			assertEquals(lost.copy(start = null, label = "Year 97 · Day 210"), stored(lost.id))
			assertEquals(kept, stored(kept.id))
			assertEquals(labelled.copy(start = null), stored(labelled.id))
			assertEquals(gregorian, service.definition().calendar)
		}

	@Test
	fun `resolveSubject summarises a scene with its chapter and people first`() = runTest(mainTestDispatcher) {
		val service = createService()
		sceneMetadataRepository.storeMetadata(
			SceneMetadata(confirmedReferences = setOf(TEAPOT, WONDERLAND, BOB, ALICE, 999)),
			sceneId = 4,
		)
		val placed = service.placeScene(4, null, null, null, false).event()

		val summary = assertIs<SubjectSummary.Scene>(service.resolveSubject(placed))

		assertEquals(4, summary.scene.id)
		assertEquals("Chapter ID 2", summary.chapterPath)
		assertEquals("Content of scene id 4", summary.excerpt)
		assertEquals(listOf(ALICE, BOB, WONDERLAND, TEAPOT), summary.cast.map { it.id })
	}

	@Test
	fun `resolveSubject summarises an entry and reports missing subjects`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeEntry(TEA_PARTY, null, null, null, false).event()
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
		val scene = service.placeScene(4, null, null, null, false).event()
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
			service.updateDefinition { it.copy(calendar = CalendarDef(CalendarPreset.GREGORIAN)) },
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

		assertEquals(TimelineResult.Ok(null), service.switchToDated(CalendarDef(coarsestUnit = "hour")))

		assertEquals(date("hour" to 0), stored(a.id).start)
		assertEquals(date("hour" to 1), stored(b.id).start)
		assertEquals(date("hour" to 2), stored(c.id).start)
	}

	@Test
	fun `displayTitles resolves a mixed list in one call`() = runTest(mainTestDispatcher) {
		val service = createService()
		val scene = service.placeScene(4, null, null, null, false).event()
		val entry = service.placeEntry(TEA_PARTY, null, null, null, false).event()
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
		assertEquals(TimelineDef.DEFAULT, service.timelineFlow.first().def)
		val calendar = CalendarDef(epochName = "The Fall", coarsestUnit = "hour")

		service.datedStory(calendar)

		val expected = TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = calendar)
		assertEquals(expected, service.timelineFlow.first { it.def.mode == TimelineMode.DATED }.def)
		assertEquals(listOf(expected), ProjectDataDatasource(ffs, toml, projDef).load().data.timelines)
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			service.updateDefinition { it.copy(calendar = CalendarDef(coarsestUnit = "fortnight")) }.error(),
		)
		assertEquals(listOf(expected), ProjectDataDatasource(ffs, toml, projDef).load().data.timelines)
	}

	@Test
	fun `lanes lists the lane order first then the rest alphabetically`() = runTest(mainTestDispatcher) {
		val service = createService()
		service.updateDefinition { it.copy(laneOrder = listOf("Queen", "Alice")) }
		service.createEvent(TimelineEventDraft(lane = "rabbit"))
		service.createEvent(TimelineEventDraft(lane = "Alice"))
		service.createEvent(TimelineEventDraft(lane = "Hatter"))
		service.createEvent(TimelineEventDraft(lane = "Hatter"))

		assertEquals(listOf("Queen", "Alice", "Hatter", "rabbit"), service.lanes())
	}

	@Test
	fun `subjectUpdates follows only the placed subject`() = runTest(mainTestDispatcher) {
		val service = createService()
		val placed = service.placeScene(4, null, null, null, false).event()
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
		val scenePlacement = service.placeScene(4, null, null, null, false).event()
		val entryPlacement = service.placeEntry(TEA_PARTY, null, null, null, false).event()
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
		val placed = service.placeScene(4, null, null, null, false).event()
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
		val placed = service.placeScene(4, null, null, null, false).event()

		assertEquals(TimelineResult.Ok(null), service.deleteEvent(placed.id))

		assertEquals(emptyList(), timeLineRepository.currentEvents())
		assertEquals("Scene ID 4", sceneRepository.getSceneItemFromId(4)?.name)
		assertEquals(TimeLineEventError.EVENT_NOT_FOUND, service.deleteEvent(placed.id).error())
	}

	private companion object {
		const val ALICE = 50
		const val WONDERLAND = 51
		const val TEA_PARTY = 52
		const val BOB = 53
		const val TEAPOT = 54
	}
}
