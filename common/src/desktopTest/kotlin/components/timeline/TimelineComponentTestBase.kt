package components.timeline

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckService
import createProject
import getProject1Def
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.time.Clock

/**
 * Timeline components over real repositories and a real [TimelineService] on a [FakeFileSystem],
 * seeded with Test Project 1 (scenes 1, 3 to 7; chapter 2 holds scenes 3 to 5) and a few entries.
 * Call [givenProject] at the start of each test, inside `runTest`.
 */
abstract class TimelineComponentTestBase : ComponentTest() {

	override val projectDef: ProjectDef = getProject1Def()

	protected lateinit var ffs: FakeFileSystem
	protected lateinit var toml: Toml
	protected lateinit var timeLineRepository: TimeLineRepository
	protected lateinit var sceneRepository: SceneRepository
	protected lateinit var sceneMetadataRepository: SceneMetadataRepository
	protected lateinit var encyclopediaRepository: EncyclopediaRepository
	protected lateinit var timelineService: TimelineService
	protected val catalog = FakeEntityCatalog()

	private val syncJournal: SyncJournal = mockk()
	private val idAllocator: IdAllocator = mockk()
	private val syncJournalDatasource: SyncJournalDatasource = mockk(relaxed = true)
	private var nextId = 100

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		nextId = 100
		coEvery { idAllocator.claimNextId() } answers { nextId++ }
		coEvery { idAllocator.findNextId() } just Runs
		every { syncJournal.isServerSynchronized() } returns false
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { syncJournal.markEntityAsDirty(any()) } just Runs
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs

		val tagIndexService = mockk<TagIndexService>(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(TagIndex.EMPTY)

		setupKoin(module {
			single { syncJournal }
			single { syncJournalDatasource }
			single { tagIndexService }
			single<ProjectSpellCheckService> {
				mockk(relaxed = true) {
					every { spellCheckAllowed } returns emptyFlow()
				}
			}
			single { timeLineRepository }
			single { timelineService }
			single { sceneRepository }
			single { encyclopediaRepository }
			single<EntityCatalog> { catalog }
			single<ImagesService> { mockk(relaxed = true) }
			single<ImagePickRequests> { mockk(relaxed = true) }
		})

		createProject(ffs, PROJECT_1_NAME)
	}

	protected suspend fun givenProject() {
		val sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		sceneRepository = SceneRepository(
			projectDef = projectDef,
			idAllocator = idAllocator,
			syncJournal = syncJournal,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = SceneDatasource(projectDef, ffs),
			clock = Clock.System,
		).initializeSceneEditor()
		sceneMetadataRepository = SceneMetadataRepository(
			projectDef = projectDef,
			sceneMetadataDatasource = sceneMetadataDatasource,
			projectMetadataDatasource = mockk(relaxed = true),
			strRes = mockk(relaxed = true),
			clock = Clock.System,
		)
		encyclopediaRepository = EncyclopediaRepository(
			projectDef = projectDef,
			idAllocator = idAllocator,
			datasource = EncyclopediaDatasource(projectDef = projectDef, toml = toml, fileSystem = ffs),
			syncJournal = syncJournal,
		)
		timeLineRepository = TimeLineRepository(projectDef, idAllocator, TimeLineDatasource(ffs, toml))
		timelineService = TimelineService(
			timeLineRepository = timeLineRepository,
			projectDataRepository = ProjectDataRepository(ProjectDataDatasource(ffs, toml, projectDef), projectDef),
			sceneRepository = sceneRepository,
			sceneMetadataRepository = sceneMetadataRepository,
			encyclopediaRepository = encyclopediaRepository,
		)

		encyclopediaRepository.createEntry("Alice", EntryType.PERSON, "A curious girl.", emptySet(), forceId = ALICE)
		encyclopediaRepository.createEntry("Wonderland", EntryType.PLACE, "Below.", emptySet(), forceId = WONDERLAND)
		encyclopediaRepository.createEntry("Tea Party", EntryType.EVENT, "The Hatter pours tea.", emptySet(), forceId = TEA_PARTY)

		catalog.items = sceneRepository.rawTree.map { it.value }
			.filter { it.type == SceneItem.Type.Scene }
			.map { CatalogItem(CatalogKind.Scene, it.id, it.name) } +
			encyclopediaRepository.readEntryDefs().map {
				CatalogItem(CatalogKind.Entry, it.id, it.name, entryType = it.type)
			}
	}

	protected fun catalogItem(id: Int): CatalogItem = catalog.items.first { it.id == id }

	companion object {
		const val ALICE = 50
		const val WONDERLAND = 51
		const val TEA_PARTY = 52
	}
}

/** The project's pickable items, as the tests set them. */
class FakeEntityCatalog(var items: List<CatalogItem> = emptyList()) : EntityCatalog {
	override suspend fun items(kinds: Set<CatalogKind>): List<CatalogItem> = items.filter { it.kind in kinds }
}
