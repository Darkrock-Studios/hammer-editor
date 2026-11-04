package repositories.timeline

import PROJECT_EMPTY_NAME
import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineEntityRules
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineRuleProblem
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.*
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.MAX_TAG_SIZE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var syncJournal: SyncJournal
	lateinit var datasource: TimeLineDatasource

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		syncJournal = mockk()
		datasource = TimeLineDatasource(ffs, toml)

		val testModule = module {
			single { syncJournal }
		}
		setupKoin(testModule)
	}

	private fun setupTimelne(
		projDef: ProjectDef = getProjectDef(PROJECT_EMPTY_NAME),
		events: List<TimeLineEvent> = fakeEvents()
	) {
		val dir = TimeLineDatasource.getTimelineDir(projDef).toOkioPath()
		ffs.createDirectories(dir)

		val timeline = TimeLineContainer(
			events = events
		)
		val text = toml.encodeToString(timeline)

		println(text)

		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.write(file) {
			writeUtf8(text)
		}
	}

	@Test
	fun `Get Timeline Dir creates Dir`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdAllocator>()

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		advanceUntilIdle()

		val timelineDir = TimeLineDatasource.getTimelineDir(projDef).toOkioPath()
		assertTrue("Timeline dir was not created") { ffs.exists(timelineDir) }
	}

	@Test
	fun `Get timeline when none exists`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdAllocator>()

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		val timeline = repo.loadTimeline()
		assertTrue("Events should have been empty") { timeline.events.isEmpty() }
	}

	@Test
	fun `Collect initial timeline`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef)

		val idRepo = mockk<IdAllocator>()
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		repo.timelineFlow.take(1).collect { timeline ->
			collectedTimeline = timeline
		}

		advanceUntilIdle()

		assertNotNull(collectedTimeline, "Did not get initial timeline")

		collectedTimeline?.let {
			assertEquals(fakeEvents(), it.events, "Loaded events did not match what was on disk")
		}
	}

	@Test
	fun `Update timeline with new event`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val idRepo = mockk<IdAllocator>()
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val content = "content"
		val date = "date"
		val id = 99
		val madeEvent = repo.createEvent(
			id = id,
			content = content,
			label = date
		)

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		collectedTimeline?.let {
			assertEquals(oldEvents.size + 1, it.events.size, "Updated events wrong size")
			assertEquals(madeEvent, it.events.last(), "Last even was not correct")
		}

		// Check the filesystem
		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		Assertions.assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(
			collectedTimeline!!.events.size,
			loadedContainer.events.size,
			"Updated events wrong size"
		)
		assertEquals(madeEvent, loadedContainer.events.last())
	}

	@Test
	fun `Update timeline with updated event`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val updatedEvent = oldEvents[2].copy(
			label = "Updated Name",
			content = "Updated Content",
		)

		val updated = repo.updateEvent(event = updatedEvent)
		assertTrue("Event was not updated") { updated }

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		// Check the memory cache
		collectedTimeline?.let {
			assertEquals(oldEvents.size, it.events.size, "Updated events wrong size")
			assertEquals(
				updatedEvent,
				it.events.find { it.id == updatedEvent.id },
				"Updated even was not correct"
			)
		}

		// Check the filesystem
		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		Assertions.assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(updatedEvent, loadedContainer.events.find { it.id == updatedEvent.id })
	}

	@Test
	fun `Create event with tags persists cleaned tags`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef, emptyList())

		val idRepo = mockk<IdAllocator>()
		coEvery { idRepo.claimNextId() } returns 42

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		val created = repo.createEvent(
			content = "An event",
			label = "Year 1",
			tags = setOf("  alpha  ", "#beta", "alpha", "with space", ""),
		)

		assertEquals(setOf("alpha", "beta"), created.tags)

		advanceUntilIdle()

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		val loaded: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(setOf("alpha", "beta"), loaded.events.find { it.id == 42 }?.tags)
	}

	@Test
	fun `Update event with tags persists cleaned tags`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()

		val updatedEvent = oldEvents[2].copy(
			tags = setOf("  alpha  ", "#beta", "with space"),
		)
		assertTrue("Event was not updated") { repo.updateEvent(event = updatedEvent) }

		advanceUntilIdle()

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		val loaded: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(setOf("alpha", "beta"), loaded.events.find { it.id == updatedEvent.id }?.tags)
	}

	@Test
	fun `Validate tags rejects tag exceeding MAX_TAG_SIZE`() {
		val repo = TimeLineRepository(
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			idAllocator = mockk(),
			datasource = datasource,
		)

		assertEquals(
			TimeLineEventError.TAG_TOO_LONG,
			repo.validateTags(setOf("x".repeat(MAX_TAG_SIZE + 1))),
		)
		assertEquals(
			TimeLineEventError.NONE,
			repo.validateTags(setOf("x".repeat(MAX_TAG_SIZE))),
		)
	}

	@Test
	fun `eventContentChangedFlow emits on create update and delete`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false
		coEvery { syncJournal.recordIdDeletion(any()) } returns Unit

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef, fakeEvents())

		val idRepo = mockk<IdAllocator>()
		coEvery { idRepo.claimNextId() } returns 99

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()
		advanceUntilIdle()

		repo.eventContentChangedFlow.test {
			val created = repo.createEvent(content = "c", label = null)
			awaitItem()

			repo.updateEvent(event = created.copy(content = "c2"))
			awaitItem()

			repo.deleteEvent(event = created)
			awaitItem()
		}
	}

	private fun referenceEvents() = listOf(
		TimeLineEvent(id = 20, order = 0, kind = TimelineKind.SCENE, subject = 4, lane = "Keep", cachedTitle = "Landfall"),
		TimeLineEvent(
			id = 21,
			order = 1,
			kind = TimelineKind.ENTRY,
			subject = 5,
			milestone = true,
			start = StoryDate(mapOf("year" to 97L)),
			end = StoryDate(mapOf("year" to 98L)),
		),
		TimeLineEvent(id = 22, order = 2, title = "Storm", content = "Wind", references = setOf(4, 9)),
		TimeLineEvent(id = 23, order = 3, title = "Unrelated", tags = setOf("x"), references = setOf(9)),
	)

	private fun TestScope.referenceRepo(projDef: ProjectDef): TimeLineRepository {
		setupTimelne(projDef, referenceEvents())
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()
		advanceUntilIdle()
		return repo
	}

	private fun storedEvents(projDef: ProjectDef): Map<Int, TimeLineEvent> {
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		return ffs.readToml<TimeLineContainer>(file, toml).events.associateBy { it.id }
	}

	@Test
	fun `findPlacement finds the placement of a subject and ignores references`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val repo = referenceRepo(getProjectDef(PROJECT_EMPTY_NAME))

		assertEquals(20, repo.findPlacement(4)?.id)
		assertEquals(21, repo.findPlacement(5)?.id)
		assertNull(repo.findPlacement(9))
	}

	@Test
	fun `remapReferencedId rewrites subjects and references without marking them dirty`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)

		val touched = repo.remapReferencedId(oldId = 4, newId = 40, subjectKind = TimelineKind.SCENE)

		assertEquals(setOf(20, 22), touched)
		val stored = storedEvents(projDef)
		assertEquals(referenceEvents()[0].copy(subject = 40), stored[20])
		assertEquals(referenceEvents()[2].copy(references = setOf(40, 9)), stored[22])
		assertEquals(referenceEvents()[1], stored[21])
		assertEquals(referenceEvents()[3], stored[23])
		assertEquals(stored.values.toSet(), repo.timelineFlow.first().events.toSet())
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	@Test
	fun `remapReferencedId touches nothing when no event refers to the id`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)

		assertEquals(emptySet(), repo.remapReferencedId(oldId = 77, newId = 78, subjectKind = TimelineKind.SCENE))
		assertEquals(referenceEvents().associateBy { it.id }, storedEvents(projDef))
	}

	@Test
	fun `detachSubject turns placements into plain events and strips references`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { syncJournal.markEntityAsDirty(any()) } returns Unit
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)

		val touched = repo.detachSubject(
			subjectId = 4,
			subjectKind = TimelineKind.SCENE,
			titleFallback = "Scene name",
			markForSync = true,
		)

		assertEquals(setOf(20, 22), touched)
		val stored = storedEvents(projDef)
		// Placement data stays; the cached title wins over the fallback.
		assertEquals(
			TimeLineEvent(id = 20, order = 0, title = "Landfall", lane = "Keep", cachedTitle = "Landfall"),
			stored[20],
		)
		assertEquals(referenceEvents()[2].copy(references = setOf(9)), stored[22])
		assertEquals(referenceEvents()[1], stored[21])
		assertEquals(referenceEvents()[3], stored[23])
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(20) }
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(22) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(21) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(23) }
	}

	@Test
	fun `detachSubject titles a placement with no cached title from the fallback`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)

		val touched = repo.detachSubject(
			subjectId = 5,
			subjectKind = TimelineKind.ENTRY,
			titleFallback = "The Siege",
			markForSync = false,
		)

		assertEquals(setOf(21), touched)
		assertEquals(
			referenceEvents()[1].copy(kind = TimelineKind.EVENT, subject = null, title = "The Siege"),
			storedEvents(projDef)[21],
		)
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	@Test
	fun `detachSubject caps a long title at the shared limit`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)

		repo.detachSubject(
			subjectId = 5,
			subjectKind = TimelineKind.ENTRY,
			titleFallback = "x".repeat(TimelineLimits.MAX_TITLE + 10),
			markForSync = false,
		)

		assertEquals("x".repeat(TimelineLimits.MAX_TITLE), storedEvents(projDef).getValue(21).title)
	}

	@Test
	fun `detachSubject converts the placement on a repository that was never initialised`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef, referenceEvents())
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		)

		val touched = repo.detachSubject(
			subjectId = 5,
			subjectKind = TimelineKind.ENTRY,
			titleFallback = "The Siege",
			markForSync = false,
		)

		assertEquals(setOf(21), touched)
		assertEquals(
			referenceEvents()[1].copy(kind = TimelineKind.EVENT, subject = null, title = "The Siege"),
			storedEvents(projDef)[21],
		)
	}

	@Test
	fun `createEvent from a draft stores every field and trims the lane`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)
		val start = StoryDate(mapOf("year" to 97L, "day" to 210L))
		val end = StoryDate(mapOf("year" to 97L, "day" to 212L))

		val created = repo.createEvent(
			TimelineEventDraft(
				title = "The storm",
				label = "Late autumn",
				content = "Wind takes the roof.",
				tags = setOf(" #weather "),
				references = setOf(4),
				lane = "  Keep  ",
				milestone = true,
				start = start,
				end = end,
				id = 30,
			)
		)

		val expected = TimeLineEvent(
			id = 30,
			order = 4,
			title = "The storm",
			label = "Late autumn",
			content = "Wind takes the roof.",
			tags = setOf("weather"),
			references = setOf(4),
			lane = "Keep",
			milestone = true,
			start = start,
			end = end,
		)
		assertEquals(expected, created)
		assertEquals(expected, storedEvents(projDef)[30])
	}

	@Test
	fun `updateEvent clears a blank lane but updateEventForSync keeps it`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val repo = referenceRepo(projDef)
		val storm = referenceEvents()[2]

		repo.updateEvent(storm.copy(lane = "   "), markForSync = false)
		assertNull(repo.currentEvents().first { it.id == 22 }.lane)

		repo.updateEventForSync(storm.copy(lane = " Keep "))
		assertEquals(" Keep ", repo.currentEvents().first { it.id == 22 }.lane)
	}

	@Test
	fun `validate checks the shape of an event`() {
		val repo = TimeLineRepository(
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			idAllocator = mockk(),
			datasource = datasource,
		)
		val event = TimeLineEvent(id = 1, order = 0)
		val placement = TimeLineEvent(id = 2, order = 0, kind = TimelineKind.SCENE, subject = 4)

		assertEquals(TimeLineEventError.NONE, repo.validate(event))
		assertEquals(TimeLineEventError.NONE, repo.validate(placement))
		assertEquals(TimeLineEventError.SUBJECT_REQUIRED, repo.validate(placement.copy(subject = null)))
		assertEquals(TimeLineEventError.SUBJECT_NOT_ALLOWED, repo.validate(event.copy(subject = 4)))
		assertEquals(TimeLineEventError.REFERENCES_NOT_ALLOWED, repo.validate(placement.copy(references = setOf(5))))
		assertEquals(TimeLineEventError.SELF_REFERENCE, repo.validate(event.copy(references = setOf(1))))
		assertEquals(
			TimeLineEventError.TAG_TOO_LONG,
			repo.validate(event.copy(tags = setOf("x".repeat(TimelineLimits.MAX_TAG + 1)))),
		)
		assertEquals(TimeLineEventError.LANE_INVALID, repo.validate(event.copy(lane = " ")))
		assertEquals(
			TimeLineEventError.LANE_INVALID,
			repo.validate(event.copy(lane = "x".repeat(TimelineLimits.MAX_LANE + 1))),
		)
		assertEquals(
			TimeLineEventError.TITLE_TOO_LONG,
			repo.validate(event.copy(title = "x".repeat(TimelineLimits.MAX_TITLE + 1))),
		)
		assertEquals(
			TimeLineEventError.LABEL_TOO_LONG,
			repo.validate(event.copy(label = "x".repeat(TimelineLimits.MAX_LABEL + 1))),
		)
		val tooManyRungs = StoryDate((0..TimelineLimits.MAX_RUNGS).associate { "unit$it" to 0L })
		assertEquals(TimeLineEventError.DATE_INVALID, repo.validate(event.copy(start = tooManyRungs)))
		assertEquals(
			TimeLineEventError.DATE_INVALID,
			repo.validate(event.copy(end = StoryDate(mapOf("x".repeat(TimelineLimits.MAX_UNIT_NAME + 1) to 1L)))),
		)
	}

	@Test
	fun `validate maps every shared rule problem to an error`() {
		val repo = TimeLineRepository(
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			idAllocator = mockk(),
			datasource = datasource,
		)
		val event = ApiProjectEntity.TimelineEventEntity(id = 1, order = 0, label = null, content = "")
		val placement = event.copy(kind = TimelineKind.SCENE, subject = 4)
		val longUnit = "x".repeat(TimelineLimits.MAX_UNIT_NAME + 1)
		val tooManyRungs = StoryDate((0..TimelineLimits.MAX_RUNGS).associate { "unit$it" to 0L })
		val oneEntityPerRule = listOf(
			placement.copy(references = setOf(4)),
			event.copy(references = setOf(1)),
			event.copy(subject = 4),
			placement.copy(subject = null),
			placement.copy(subject = 1),
			placement.copy(references = setOf(5)),
			placement.copy(content = "Text"),
			placement.copy(tags = setOf("tag")),
			event.copy(lane = " "),
			event.copy(lane = "x".repeat(TimelineLimits.MAX_LANE + 1)),
			event.copy(title = "x".repeat(TimelineLimits.MAX_TITLE + 1)),
			event.copy(cachedTitle = "x".repeat(TimelineLimits.MAX_TITLE + 1)),
			event.copy(label = "x".repeat(TimelineLimits.MAX_LABEL + 1)),
			event.copy(tags = setOf("x".repeat(TimelineLimits.MAX_TAG + 1))),
			event.copy(start = tooManyRungs),
			event.copy(start = StoryDate(mapOf(" " to 1L))),
			event.copy(end = StoryDate(mapOf(longUnit to 1L))),
			event.copy(timeline = "not valid"),
			event.copy(timeline = TimelineDef.MAIN_ID),
		)

		val problems = oneEntityPerRule.map { assertNotNull(TimelineEntityRules.problem(it)) }

		assertEquals(TimelineRuleProblem::class.sealedSubclasses.toSet(), problems.map { it::class }.toSet())
		problems.zip(oneEntityPerRule).forEach { (problem, entity) ->
			assertNotEquals(TimeLineEventError.NONE, problem.toEventError(), "$problem")
			// TimeLineEvent has no timeline field, so a timeline problem cannot reach validate.
			if (entity.timeline == null) {
				assertEquals(problem.toEventError(), repo.validate(entity.toTimeLineEvent()), "$problem")
			}
		}
	}

	@Test
	fun `Concurrent updates to different events both persist`() = runTest {
		every { syncJournal.isServerSynchronized() } returns false
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()
		advanceUntilIdle()

		launch { repo.updateEvent(oldEvents[1].copy(content = "First")) }
		launch { repo.updateEvent(oldEvents[2].copy(content = "Second")) }
		advanceUntilIdle()

		val stored = storedEvents(projDef)
		assertEquals("First", stored.getValue(oldEvents[1].id).content)
		assertEquals("Second", stored.getValue(oldEvents[2].id).content)
	}
}
