package repositories.duplicateids

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
					)
				}
				scoped { EncyclopediaDatasource(projectDef, toml, ffs) }
				scoped { EncyclopediaRepository(projectDef, get(), get(), get()) }
				scoped<EncyclopediaService> { mockk(relaxed = true) }
				scoped { referenceRemapper }
				scoped { ClientSceneSynchronizer(projectDef, get(), get(), get(), get(), get(), TestStrRes()) }
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
		service = DuplicateIdService(projectDef, rationalizer, syncJournal, mockk(relaxed = true))
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
	}

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
}
