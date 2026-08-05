package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Re-padding a directory's order digits renames every sibling in turn. Each rename drops the path
 * cache, so the loop stays linear only while it reads nothing from that cache between renames. This
 * pins that: put a cache read back inside the loop and the scan count grows with the sibling count.
 */
class SceneOrderRescanTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var countingFs: CountingFileSystem
	private lateinit var repository: SceneRepository

	private class CountingFileSystem(delegate: FakeFileSystem) : ForwardingFileSystem(delegate) {
		var listRecursivelyCount = 0
			private set

		override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> {
			listRecursivelyCount++
			return super.listRecursively(dir, followSymlinks)
		}
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, PROJECT_1_NAME)
		countingFs = CountingFileSystem(ffs)

		val projectDef = getProjectDef(PROJECT_1_NAME)
		val toml = createTomlSerializer()
		val syncJournal: SyncJournal = mockk(relaxed = true)
		every { syncJournal.isServerSynchronized() } returns false
		val idAllocator: IdAllocator = mockk(relaxed = true)
		coEvery { idAllocator.claimNextId() } returnsMany (100..400).toList()

		val datasource = SceneDatasource(projectDef, countingFs)
		repository = SceneRepository(
			projectDef = projectDef,
			syncJournal = syncJournal,
			idAllocator = idAllocator,
			sceneMetadataDatasource = SceneMetadataDatasource(countingFs, toml, projectDef),
			sceneDatasource = datasource,
			clock = Clock.System,
		)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `crossing the order-digit boundary does not re-scan once per renamed sibling`() = runTest {
		repository.initializeSceneEditor()

		// Ten siblings means the next order is 10, which widens the field and re-pads all of them.
		while (repository.rawTree.root().numChildrenImmedate() < 10) {
			assertNotNull(repository.createScene(parent = null, sceneName = "Filler"))
		}
		val siblingsBefore = repository.rawTree.root().numChildrenImmedate()
		val scansBefore = countingFs.listRecursivelyCount

		// This create takes the order to 10, re-padding every existing sibling to two digits.
		assertNotNull(repository.createScene(parent = null, sceneName = "Boundary"))

		val scansForRepad = countingFs.listRecursivelyCount - scansBefore
		assertTrue(
			scansForRepad < siblingsBefore,
			"Re-padding $siblingsBefore siblings took $scansForRepad scans; it must not scale with them",
		)

		// The renames still have to land correctly.
		assertEquals(
			siblingsBefore + 1,
			repository.rawTree.root().numChildrenImmedate(),
			"Every sibling survives the re-pad",
		)
		assertTrue(
			repository.getScenes().all { repository.resolveScenePathFromFilesystem(it.id) != null },
			"Every scene in the project resolves at its new name",
		)
	}
}
