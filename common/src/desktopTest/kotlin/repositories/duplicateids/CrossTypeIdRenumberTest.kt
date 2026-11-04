package repositories.duplicateids

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.ImageIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.references.TimelineReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientImageSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.scope.Scope
import org.koin.dsl.module
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** Renumbering runs through each store's real re-id; only the server side is mocked. */
class CrossTypeIdRenumberTest : RationalizerTestBase() {

	private lateinit var syncJournal: SyncJournal
	private lateinit var referenceRemapper: ReferenceRemapper
	private lateinit var projectScope: Scope
	private lateinit var service: DuplicateIdService

	@BeforeEach
	override fun setup() {
		super.setup()
		syncJournal = mockk(relaxed = true)
		every { syncJournal.isServerSynchronized() } returns false
		referenceRemapper = mockk(relaxed = true)
		coEvery { referenceRemapper.remapEntryReferences(any(), any()) } returns emptySet()
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncJournal }
				scoped<ServerProjectApi> { mockk(relaxed = true) }
				scoped<ProjectMetadataDatasource> { mockk(relaxed = true) }
				scoped { SceneDatasource(projectDef, ffs) }
				scoped { SceneMetadataDatasource(ffs, toml, projectDef) }
				scoped { SceneRepository(projectDef, get(), get(), get(), get(), Clock.System) }
				scoped { SceneContentRepository(projectDef, get()) }
				scoped { SceneDraftsDatasource(projectDef, ffs) }
				scoped { SceneDraftRepository(projectDef, get(), Clock.System) }
				scoped {
					SceneMetadataRepository(
						projectDef = projectDef,
						sceneMetadataDatasource = get(),
						projectMetadataDatasource = get(),
						strRes = TestStrRes(),
						clock = Clock.System,
					)
				}
				scoped {
					SceneEditorService(
						sceneEditorRepository = get(),
						sceneContentRepository = get(),
						sceneMetadataRepository = get(),
						referenceIndexRepository = mockk<ReferenceIndexRepository>(relaxed = true),
						statisticsRepository = mockk<StatisticsRepository>(relaxed = true),
						writingSessionTracker = mockk<WritingSessionTracker>(relaxed = true),
						timeLineRepository = get(),
					)
				}
				scoped { EncyclopediaDatasource(projectDef, toml, ffs) }
				scoped { EncyclopediaRepository(projectDef, get(), get(), get()) }
				scoped<EncyclopediaService> { mockk(relaxed = true) }
				scoped { referenceRemapper }
				scoped { TimeLineDatasource(ffs, toml) }
				scoped { TimeLineRepository(projectDef, get(), get()) }
				scoped { TimelineReferenceRemapper(get()) }
				scoped { ClientSceneSynchronizer(projectDef, get(), get(), get(), get(), get(), get(), TestStrRes()) }
				scoped { ClientEncyclopediaSynchronizer(projectDef, get(), TestStrRes()) }
				scoped<ClientNoteSynchronizer> { mockk(relaxed = true) }
				scoped<ClientTimelineSynchronizer> { mockk(relaxed = true) }
				scoped<ClientSceneDraftSynchronizer> { mockk(relaxed = true) }
				scoped { ImagesDatasource(projectDef, ffs) }
				scoped { ImagesRepository(projectDef, get(), get(), get()) }
				scoped { ClientImageSynchronizer(projectDef, get(), TestStrRes()) }
				scoped { EntitySynchronizers(projectDef) }
				factory { SceneIdDatasource(ffs) }
				factory { NotesIdDatasource(ffs) }
				factory { EncyclopediaIdDatasource(ffs) }
				factory { TimeLineEventIdDatasource(ffs, toml) }
				factory { SceneDraftIdDatasource(ffs) }
				factory { ImageIdDatasource(ffs) }
				scoped { IdAllocator(projectDef) }
			}
		})
		projectScope = createProjectScope(projectDef)
		service = DuplicateIdService(projectDef, rationalizer, syncJournal)
	}

	@AfterEach
	override fun tearDown() {
		projectScope.get<SceneContentRepository>().onScopeClose(mockk())
		super.tearDown()
	}

	/** What project open does before this step: load the tree and find the next free id. */
	private suspend fun openStores() {
		projectScope.get<SceneRepository>().initializeSceneEditor()
		projectScope.get<SceneContentRepository>().initialize()
		projectScope.get<EncyclopediaRepository>().loadEntriesImperative()
		projectScope.get<TimeLineRepository>().initialize()
	}

	private fun writeTimeline(vararg events: TimeLineEvent) {
		write("timeline/timeline.toml", toml.encodeToString(TimeLineContainer.serializer(), TimeLineContainer(events.toList())))
	}

	private suspend fun storedTimeline(): Map<Int, TimeLineEvent> =
		projectScope.get<TimeLineRepository>().loadTimeline().events.associateBy { it.id }

	private fun serverLastSaw(id: Int, hash: String) {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.loadSyncData() } returns ProjectSynchronizationData(
			lastId = id,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(0),
			dirty = emptyList(),
			deletedIds = emptySet(),
			syncedHashes = mapOf(id to hash),
		)
	}

	private fun writeSceneAndEntrySharingSix() {
		write("scenes/0~Duel~6.md", "En garde")
		write("encyclopedia/person/person~6~Bob.toml", entryToml(6, "Bob", "A blacksmith"))
	}

	@Test
	fun `A scene keeps a shared id and the entry gets a fresh one`() = runTest {
		writeSceneAndEntrySharingSix()
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~6.md"), filesIn("scenes"))
		assertEquals(listOf("person/person~7~Bob.toml"), filesIn("encyclopedia"))
		assertEquals(7, toml.decodeFromString<EntryContainer>(read("encyclopedia/person/person~7~Bob.toml")).entry.id)
		assertEquals(emptyMap(), rationalizer.findCrossTypeIds())
	}

	@Test
	fun `Referrers rewritten by renumbering are marked dirty`() = runTest {
		writeSceneAndEntrySharingSix()
		openStores()
		coEvery { referenceRemapper.remapEntryReferences(6, 7) } returns setOf(3, 4)

		service.resolveCrossTypeIds()

		assertEquals(listOf("person/person~7~Bob.toml"), filesIn("encyclopedia"))
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(3) }
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(4) }
	}

	@Test
	fun `An image keeps a shared id and the scene gets a fresh one`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		val image = projectDir / "images" / "6~Map.png"
		ffs.createDirectories(image.parent!!)
		ffs.write(image) { write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) }
		openStores()

		service.resolveCrossTypeIds()

		// Other entities reference images by id, so the image is the keeper.
		assertEquals(listOf("6~Map.png"), filesIn("images"))
		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes"))
		assertEquals(emptyMap(), rationalizer.findCrossTypeIds())
	}

	@Test
	fun `The item the server last saw keeps the id`() = runTest {
		writeSceneAndEntrySharingSix()
		openStores()
		val entryHash = projectScope.get<ClientEncyclopediaSynchronizer>().getEntityHash(6)
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.loadSyncData() } returns ProjectSynchronizationData(
			lastId = 6,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(0),
			dirty = emptyList(),
			deletedIds = emptySet(),
			syncedHashes = mapOf(6 to entryHash),
		)

		service.resolveCrossTypeIds()

		assertEquals(listOf("person/person~6~Bob.toml"), filesIn("encyclopedia"))
		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes"))
	}

	@Test
	fun `Nothing is renumbered while a store still has copies to resolve`() = runTest {
		writeSceneAndEntrySharingSix()
		write("scenes/1~Duel copy~6.md", "Touche")
		service.rationalizeOnOpen()
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("person/person~6~Bob.toml"), filesIn("encyclopedia"))
		assertEquals(listOf("0~Duel~6.md", "1~Duel copy~6.md"), filesIn("scenes"))
	}

	@Test
	fun `An unreadable entry doesn't stop the project opening`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		write("encyclopedia/person/person~6~Bob.toml", "not toml at all [[")
		openStores()
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.loadSyncData() } returns ProjectSynchronizationData(
			lastId = 6,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(0),
			dirty = emptyList(),
			deletedIds = emptySet(),
			syncedHashes = mapOf(6 to "server-hash"),
		)

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~6.md"), filesIn("scenes"))
		assertEquals(listOf("person/person~7~Bob.toml"), filesIn("encyclopedia"))
	}

	@Test
	fun `A renumbered scene keeps its unsaved edits`() = runTest {
		writeSceneAndEntrySharingSix()
		write("scenes/.buffers/6.md", "Unsaved edits")
		openStores()
		serverLastSaw(6, projectScope.get<ClientEncyclopediaSynchronizer>().getEntityHash(6))

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes").filterNot { it.startsWith(".") })
		assertEquals(listOf("7.md"), filesIn("scenes/.buffers"))
		val buffers = projectScope.get<SceneContentRepository>()
		assertEquals(null, buffers.getSceneBuffer(6))
		assertEquals("Unsaved edits", buffers.getSceneBuffer(7)?.content?.markdown)
		assertEquals(7, buffers.getSceneBuffer(7)?.content?.scene?.id)
	}

	@Test
	fun `A renumbered entry takes its timeline placement along and the event goes dirty`() = runTest {
		writeSceneAndEntrySharingSix()
		writeTimeline(
			TimeLineEvent(id = 1, order = 0, kind = TimelineKind.ENTRY, subject = 6, cachedTitle = "Bob"),
			TimeLineEvent(id = 2, order = 1, title = "Unrelated", references = setOf(3)),
		)
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("person/person~7~Bob.toml"), filesIn("encyclopedia"))
		assertEquals(7, storedTimeline().getValue(1).subject)
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(1) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `A renumbered scene takes its timeline placement along and the event goes dirty`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		val image = projectDir / "images" / "6~Map.png"
		ffs.createDirectories(image.parent!!)
		ffs.write(image) { write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) }
		writeTimeline(
			TimeLineEvent(id = 1, order = 0, kind = TimelineKind.SCENE, subject = 6, cachedTitle = "Duel"),
			TimeLineEvent(id = 2, order = 1, title = "Before the duel", references = setOf(6)),
		)
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes"))
		val timeline = storedTimeline()
		assertEquals(7, timeline.getValue(1).subject)
		assertEquals(setOf(7), timeline.getValue(2).references)
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(1) }
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `Renumbering an entry moves only the entry placement when a scene shares its id`() = runTest {
		write("scenes/0~Duel~7.md", "En garde")
		write("encyclopedia/person/person~7~Bob.toml", entryToml(7, "Bob", "A blacksmith"))
		writeTimeline(
			TimeLineEvent(id = 1, order = 0, kind = TimelineKind.SCENE, subject = 7, cachedTitle = "Duel"),
			TimeLineEvent(id = 2, order = 1, kind = TimelineKind.ENTRY, subject = 7, cachedTitle = "Bob"),
		)
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes"))
		assertEquals(listOf("person/person~8~Bob.toml"), filesIn("encyclopedia"))
		val timeline = storedTimeline()
		assertEquals(7, timeline.getValue(1).subject)
		assertEquals(8, timeline.getValue(2).subject)
	}

	@Test
	fun `A renumbered scene takes its placements on every timeline along and each goes dirty`() = runTest {
		write("scenes/0~Duel~6.md", "En garde")
		val image = projectDir / "images" / "6~Map.png"
		ffs.createDirectories(image.parent!!)
		ffs.write(image) { write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) }
		writeTimeline(
			TimeLineEvent(id = 1, order = 0, kind = TimelineKind.SCENE, subject = 6, cachedTitle = "Duel"),
			TimeLineEvent(
				id = 2,
				order = 0,
				kind = TimelineKind.SCENE,
				subject = 6,
				cachedTitle = "Duel",
				timeline = "a3f09c1e",
			),
			TimeLineEvent(id = 3, order = 1, title = "Unrelated", timeline = "a3f09c1e"),
		)
		openStores()

		service.resolveCrossTypeIds()

		assertEquals(listOf("0~Duel~7.md"), filesIn("scenes"))
		val timeline = storedTimeline()
		assertEquals(7, timeline.getValue(1).subject)
		assertEquals(7, timeline.getValue(2).subject)
		assertEquals(null, timeline.getValue(1).timeline)
		assertEquals("a3f09c1e", timeline.getValue(2).timeline)
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(1) }
		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(2) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(3) }
	}
}
