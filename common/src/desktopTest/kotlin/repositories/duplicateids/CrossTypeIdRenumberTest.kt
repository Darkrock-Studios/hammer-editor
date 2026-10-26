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
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.mockk.coEvery
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
	private lateinit var projectScope: Scope
	private lateinit var service: DuplicateIdService

	@BeforeEach
	override fun setup() {
		super.setup()
		syncJournal = mockk(relaxed = true)
		every { syncJournal.isServerSynchronized() } returns false
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
				scoped { SceneDraftsDatasource(ffs, get()) }
				scoped { SceneDraftRepository(projectDef, get(), get(), Clock.System) }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped { EncyclopediaDatasource(projectDef, toml, ffs, mockk()) }
				scoped { EncyclopediaRepository(projectDef, get(), get(), get()) }
				scoped<EncyclopediaService> { mockk(relaxed = true) }
				scoped<ReferenceRemapper> { mockk(relaxed = true) }
				scoped { ClientSceneSynchronizer(projectDef, get(), get(), get(), get(), get(), TestStrRes()) }
				scoped { ClientEncyclopediaSynchronizer(projectDef, get(), get(), TestStrRes()) }
				scoped<ClientNoteSynchronizer> { mockk(relaxed = true) }
				scoped<ClientTimelineSynchronizer> { mockk(relaxed = true) }
				scoped<ClientSceneDraftSynchronizer> { mockk(relaxed = true) }
				scoped { EntitySynchronizers(projectDef) }
				factory { SceneIdDatasource(ffs) }
				factory { NotesIdDatasource(ffs) }
				factory { EncyclopediaIdDatasource(ffs) }
				factory { TimeLineEventIdDatasource(ffs, toml) }
				factory { SceneDraftIdDatasource(get()) }
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
		projectScope.get<EncyclopediaRepository>().loadEntriesImperative()
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
}
