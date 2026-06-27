package synchronizer

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import createProject
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Classical-style: drives a real [SceneDraftRepository] / [SceneDraftsDatasource] over a
 * [FakeFileSystem] seeded from the "Test Project 2" fixture. Only the network boundary is
 * mocked. Asserts observable filesystem outcomes (no file escapes the drafts directory).
 */
class ClientSceneDraftSynchronizerSecurityTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)
	private val strRes: StrRes = TestStrRes()

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var idAllocator: IdAllocator

	@MockK(relaxed = true)
	private lateinit var sceneContentRepository: SceneContentRepository

	@MockK(relaxed = true)
	private lateinit var clock: Clock

	private lateinit var ffs: FakeFileSystem
	private lateinit var repository: SceneDraftRepository
	private lateinit var datasource: SceneDraftsDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		ffs = FakeFileSystem()
		createProject(ffs, PROJECT_2_NAME)

		val sceneDatasource = SceneDatasource(projectDef, ffs)
		datasource = SceneDraftsDatasource(ffs, sceneDatasource)

		setupKoin(module {
			single { idAllocator }
			single { clock }
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { repository }
			}
		})

		repository = SceneDraftRepository(projectDef, sceneContentRepository, datasource, clock)
	}

	private fun newSynchronizer() = ClientSceneDraftSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	private fun draftEntity(name: String) = ApiProjectEntity.SceneDraftEntity(
		id = 500,
		sceneId = 1,
		created = Instant.fromEpochSeconds(1000),
		name = name,
		content = "PWNED",
	)

	private fun allFiles(): Set<String> =
		ffs.allPaths
			.filter { ffs.metadataOrNull(it)?.isRegularFile == true }
			.map { it.toString() }
			.toSet()

	@Test
	fun `a traversal draft name escaping with dot-dot is rejected and writes no file`() = runTest {
		val before = allFiles()
		val logs = mutableListOf<SyncLogMessage>()

		val stored = newSynchronizer().storeEntity(
			draftEntity("../../../../evil"),
			syncId = "sync",
			onLog = { logs.add(it) },
		)

		assertFalse(stored, "Malicious draft must be skipped")
		assertEquals(before, allFiles(), "No file may be created anywhere on the filesystem")
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
	}

	@Test
	fun `a draft name with an embedded slash is rejected and writes no file`() = runTest {
		val before = allFiles()

		val stored = newSynchronizer().storeEntity(
			draftEntity("a/b"),
			syncId = "sync",
			onLog = {},
		)

		assertFalse(stored)
		assertEquals(before, allFiles())
	}

	@Test
	fun `a normal draft name is stored and loads back`() = runTest {
		val entity = draftEntity("Clean Name")

		val stored = newSynchronizer().storeEntity(entity, syncId = "sync", onLog = {})

		assertTrue(stored)
		val def = repository.getDraftDef(500)
		assertEquals("Clean Name", def?.draftName)
		assertEquals("PWNED", repository.loadDraftContent(def!!))
	}
}
