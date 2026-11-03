package repositories

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.ImageIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.findImageFile
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.displayOrder
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testProjectId
import utils.BaseTest
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Installs the bundled example project the way the app does and checks its Timeline 2.0 content. */
class ExampleProjectTimelineTest : BaseTest() {

	private val toml = createTomlSerializer()
	private val projectsDir = "/projects".toPath()
	private val projectDir = projectsDir / ExampleProjectRepository.PROJECT_NAME
	private val projDef = ProjectDef(
		id = testProjectId(ExampleProjectRepository.PROJECT_NAME),
		name = ExampleProjectRepository.PROJECT_NAME,
		path = projectDir.toHPath(),
	)
	private val zipBytes: ByteArray = ExampleProjectRepository::class.java
		.getResourceAsStream("/raw/${ExampleProjectRepository.EXAMPLE_PROJECT_FILE_NAME}")!!
		.use { it.readBytes() }

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(module {
			single<SyncJournal> { mockk(relaxed = true) }
			single<SyncJournalDatasource> { mockk(relaxed = true) }
		})
		ffs = FakeFileSystem()
		unzipBytesToDirectory(ffs, zipBytes, projectsDir)
	}

	private fun loadTimeline(): TimeLineContainer =
		assertNotNull(
			TimeLineDatasource.loadTimelineOrNull(TimeLineDatasource.getTimelineFilePath(projDef), ffs, toml),
			"timeline.toml must decode",
		)

	private fun sceneIdsOnDisk(): Set<Int> {
		val scenesDir = projectDir / SceneDatasource.SCENE_DIRECTORY
		return ffs.listRecursively(scenesDir)
			.filter { path -> path.relativeTo(scenesDir).segments.none { it.startsWith(".") } }
			.mapNotNull { SceneDatasource.SCENE_FILENAME_PATTERN.matchEntire(it.name) }
			.filter { it.groupValues[4] == ".md" }
			.map { it.groupValues[3].toInt() }
			.toSet()
	}

	private fun entryIdsOnDisk(): Set<Int> =
		ffs.listRecursively(projectDir / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY)
			.mapNotNull { EncyclopediaDatasource.ENTRY_FILENAME_PATTERN.matchEntire(it.name) }
			.map { it.groupValues[2].toInt() }
			.toSet()

	@Test
	fun `zip holds only the project folder and no OS metadata`() {
		val names = mutableListOf<String>()
		ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
			while (true) names += (zip.nextEntry ?: break).name
		}

		assertTrue(names.isNotEmpty())
		assertTrue(names.all { it.startsWith("${ExampleProjectRepository.PROJECT_NAME}/") }, names.toString())
		assertTrue(names.none { "__MACOSX" in it || it.substringAfterLast('/') == ".DS_Store" }, names.toString())
	}

	@Test
	fun `installed project is at the current data version and carries no id`() {
		val metadata = assertNotNull(ProjectMetadataDatasource(ffs, toml).readMetadata(projDef))

		assertEquals(PROJECT_DATA_VERSION, metadata.info.dataVersion)
		assertNull(metadata.info.projectId)
	}

	@Test
	fun `settings define the dated main timeline and keep the project data`() = runTest(mainTestDispatcher) {
		val data = ProjectDataDatasource(ffs, toml, projDef).load().data

		val expected = TimelineDef(
			id = TimelineDef.MAIN_ID,
			name = "Main",
			mode = TimelineMode.DATED,
			calendar = CalendarDef(CalendarPreset.STORY, epochName = "The Fall", coarsestUnit = StoryCalendar.HOUR),
			laneOrder = listOf("Alice", "Alice's size", "The Queen"),
		)
		assertEquals(listOf(expected), data.timelines)
		assertEquals("Lewis Carroll", data.authorName)
		assertEquals("en-US", data.language)
		assertNotNull(data.theme)
		assertNotNull(data.wordCountGoal)
		assertNotNull(findImageFile(projDef, assertNotNull(data.coverImageId), ffs), "cover image must exist")
	}

	@Test
	fun `timeline has the expected shape`() = runTest(mainTestDispatcher) {
		val events = loadTimeline().events
		val stored = TimeLineDatasource(ffs, toml).loadTimeline(projDef).events

		assertEquals(events, stored)
		assertEquals(16, events.count { it.kind == TimelineKind.SCENE })
		assertEquals(listOf(34), events.filter { it.kind == TimelineKind.ENTRY }.mapNotNull { it.subject })
		assertEquals(
			setOf("Down the rabbit hole", "Alice wakes on the bank"),
			events.filter { it.milestone }.mapNotNullTo(HashSet()) { it.title },
		)
		assertEquals(events.size, events.map { it.id }.toSet().size)
		assertTrue(events.all { it.id > 72 }, "ids must be above the legacy events and every other entity")
		assertEquals(events.indices.toList(), events.map { it.order })
	}

	@Test
	fun `timeline ids collide with no other entity`() {
		val sources = mapOf(
			"scenes" to SceneIdDatasource(ffs),
			"notes" to NotesIdDatasource(ffs),
			"entries" to EncyclopediaIdDatasource(ffs),
			"drafts" to SceneDraftIdDatasource(ffs),
			"images" to ImageIdDatasource(ffs),
		).mapValues { (_, source) -> assertNotNull(source.findIds(projDef)) }
		val timelineIds = assertNotNull(TimeLineEventIdDatasource(ffs, toml).findIds(projDef))

		assertEquals(6, sources.getValue("images").size, "every entry image is promoted to an Image, plus the cover")
		sources.forEach { (name, ids) ->
			assertTrue((ids intersect timelineIds).isEmpty(), "timeline ids collide with $name")
		}
	}

	@Test
	fun `dates are valid hour and minute dates in story order`() = runTest(mainTestDispatcher) {
		val calendar = ProjectDataDatasource(ffs, toml, projDef).load().data.timelines.single().calendar!!
		val events = loadTimeline().events
		val rungs = setOf(StoryCalendar.HOUR, StoryCalendar.MINUTE)

		events.forEach { event ->
			val start = assertNotNull(event.start, "event ${event.id} needs a start")
			listOfNotNull(start, event.end).forEach { date ->
				assertNull(StoryCalendar.validate(calendar, date), "event ${event.id}: $date")
				assertEquals(rungs, date.rungs.keys, "event ${event.id}: $date")
				assertTrue(date[StoryCalendar.HOUR]!! in 0L..6L, "event ${event.id}: $date")
			}
			event.end?.let { end ->
				assertTrue(
					StoryCalendar.position(calendar, end)!! > StoryCalendar.position(calendar, start)!!,
					"event ${event.id} ends before it starts",
				)
			}
		}
		assertEquals(events, displayOrder(events, TimelineMode.DATED, calendar))
	}

	@Test
	fun `placements and references resolve against the project`() = runTest(mainTestDispatcher) {
		val events = loadTimeline().events
		val sceneIds = sceneIdsOnDisk()
		val entryIds = entryIdsOnDisk()

		val placedScenes = events.filter { it.kind == TimelineKind.SCENE }.mapNotNull { it.subject }
		assertEquals(sceneIds - TITLE_PAGE_SCENE, placedScenes.toSet())
		assertEquals(placedScenes.size, placedScenes.toSet().size)
		events.filter { it.kind == TimelineKind.ENTRY }.forEach { assertTrue(it.subject!! in entryIds) }
		events.forEach { event ->
			assertTrue(entryIds.containsAll(event.references), "event ${event.id}: ${event.references - entryIds}")
		}

		val service = createService()
		events.filter { it.kind == TimelineKind.SCENE }.forEach { event ->
			val summary = assertIs<SubjectSummary.Scene>(service.resolveSubject(event))
			assertEquals(event.cachedTitle, summary.scene.name)
		}
		val tea = events.single { it.kind == TimelineKind.ENTRY }
		val entry = assertIs<SubjectSummary.Entry>(service.resolveSubject(tea))
		assertEquals(tea.cachedTitle, entry.entry.name)
		assertNotNull(entry.primaryImageId, "the entry's image is an Image entity")
	}

	@Test
	fun `lanes follow the lane order`() = runTest(mainTestDispatcher) {
		val service = createService()
		val laneOrder = service.definition().laneOrder

		assertEquals(laneOrder, service.lanes())
		assertEquals(laneOrder.toSet(), loadTimeline().events.mapNotNullTo(HashSet()) { it.lane })
	}

	private suspend fun createService(): TimelineService {
		val idAllocator = mockk<IdAllocator>(relaxed = true)
		val syncJournal = mockk<SyncJournal>(relaxed = true)
		val sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projDef)
		val sceneRepository = SceneRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			syncJournal = syncJournal,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = SceneDatasource(projDef, ffs),
			clock = Clock.System,
		).initializeSceneEditor()
		return TimelineService(
			timeLineRepository = TimeLineRepository(projDef, idAllocator, TimeLineDatasource(ffs, toml)),
			projectDataRepository = ProjectDataRepository(ProjectDataDatasource(ffs, toml, projDef), projDef),
			sceneRepository = sceneRepository,
			sceneMetadataRepository = SceneMetadataRepository(
				projectDef = projDef,
				sceneMetadataDatasource = sceneMetadataDatasource,
				projectMetadataDatasource = mockk(relaxed = true),
				strRes = mockk(relaxed = true),
				clock = Clock.System,
			),
			encyclopediaRepository = EncyclopediaRepository(
				projectDef = projDef,
				idAllocator = idAllocator,
				datasource = EncyclopediaDatasource(projectDef = projDef, toml = toml, fileSystem = ffs),
				syncJournal = syncJournal,
			),
		)
	}

	private companion object {
		const val TITLE_PAGE_SCENE = 1
	}
}
