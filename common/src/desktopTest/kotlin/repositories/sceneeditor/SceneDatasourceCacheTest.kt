package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
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

/**
 * Verifies the scene-path scan cache: reads reuse a single recursive scan, and every structural
 * mutation invalidates it so results never go stale.
 */
class SceneDatasourceCacheTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var countingFs: CountingFileSystem
	private lateinit var sceneDatasource: SceneDatasource

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
		sceneDatasource = SceneDatasource(getProjectDef(PROJECT_1_NAME), countingFs)
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `getAllScenePaths caches the recursive scan`() = runTest {
		val first = sceneDatasource.getAllScenePaths()
		val scansAfterFirst = countingFs.listRecursivelyCount
		assertEquals(1, scansAfterFirst, "First call should scan the filesystem once")

		// Repeated reads must not re-walk the tree.
		repeat(10) { sceneDatasource.getAllScenePaths() }
		assertEquals(
			scansAfterFirst,
			countingFs.listRecursivelyCount,
			"Cached reads must not trigger additional recursive scans"
		)
		assertTrue(first.isNotEmpty(), "Project should have scenes")
	}

	@Test
	fun `structural mutation invalidates the cache and returns fresh results`() = runTest {
		val before = sceneDatasource.getAllScenePaths()
		val newId = 999
		val newPath = sceneDatasource.getSceneDirectory().toOkioPath()
			.div("5~Freshly Added~$newId.md").toHPath()

		// createNewGroup writes a new scene file; the cache must be invalidated.
		sceneDatasource.createNewGroup(newPath)

		val scansBefore = countingFs.listRecursivelyCount
		val after = sceneDatasource.getAllScenePaths()
		assertEquals(
			scansBefore + 1,
			countingFs.listRecursivelyCount,
			"A mutation must force the next read to re-scan"
		)
		assertEquals(before.size + 1, after.size, "New scene must appear after invalidation")
		assertNotNull(
			sceneDatasource.resolveScenePathFromFilesystem(newId),
			"Newly created scene must be resolvable (no stale cache)"
		)
	}

	@Test
	fun `moveScene invalidates the cache`() = runTest {
		val sourceId = 1
		val sourcePath = sceneDatasource.resolveScenePathFromFilesystem(sourceId)
		assertNotNull(sourcePath)

		val targetPath = sceneDatasource.getSceneDirectory().toOkioPath()
			.div("9~Renamed~$sourceId.md").toHPath()

		sceneDatasource.moveScene(sourcePath, targetPath)

		// After a move the old path must be gone and the new one present.
		val paths = sceneDatasource.getAllScenePaths().map { it.toOkioPath() }
		assertTrue(paths.contains(targetPath.toOkioPath()), "Moved-to path must be present")
		assertTrue(!paths.contains(sourcePath.toOkioPath()), "Moved-from path must be gone")
	}
}
