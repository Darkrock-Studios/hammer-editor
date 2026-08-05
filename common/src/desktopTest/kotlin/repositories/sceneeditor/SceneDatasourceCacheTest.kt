package repositories.sceneeditor

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
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
 * Verifies the scene-path scan cache: reads reuse a single recursive scan, creation updates the
 * cache in place, and other structural mutations invalidate it, so results never go stale.
 */
class SceneDatasourceCacheTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var countingFs: CountingFileSystem
	private lateinit var sceneDatasource: SceneDatasource

	private class CountingFileSystem(delegate: FakeFileSystem) : ForwardingFileSystem(delegate) {
		var listRecursivelyCount = 0
			private set
		var listCount = 0
			private set

		override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> {
			listRecursivelyCount++
			return super.listRecursively(dir, followSymlinks)
		}

		override fun list(dir: Path): List<Path> {
			listCount++
			return super.list(dir)
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
	fun `creating a scene updates the warm cache in place without re-scanning`() = runTest {
		val before = sceneDatasource.getAllScenePaths()
		val newId = 999
		val newPath = sceneDatasource.getSceneDirectory().toOkioPath()
			.div("5~Freshly Added~$newId.md").toHPath()

		val scansBefore = countingFs.listRecursivelyCount
		// createNewGroup writes a new scene file; the warm cache absorbs it without a re-scan.
		sceneDatasource.createNewGroup(newPath)

		val after = sceneDatasource.getAllScenePaths()
		assertEquals(
			scansBefore,
			countingFs.listRecursivelyCount,
			"Creation must update the warm cache in place, not trigger a recursive re-scan"
		)
		assertEquals(before.size + 1, after.size, "New scene must appear in the cache")
		assertNotNull(
			sceneDatasource.resolveScenePathFromFilesystem(newId),
			"Newly created scene must be resolvable (cache stays fresh)"
		)
	}

	@Test
	fun `rewriting scene content leaves the warm cache valid`() = runTest {
		// Warm the cache, then rewrite a leaf scene's markdown in place.
		val before = sceneDatasource.getAllScenePaths()
		val scenePath = before.first { sceneDatasource.getSceneFromPath(it).type == SceneItem.Type.Scene }
		val sceneItem = sceneDatasource.getSceneFromPath(scenePath)
		val scansBefore = countingFs.listRecursivelyCount

		val stored = sceneDatasource.storeSceneMarkdownRaw(
			SceneContent(sceneItem, "Freshly rewritten content"),
			scenePath,
		)
		assertTrue(stored, "Content rewrite should succeed")

		// A content write doesn't move the file, so the path cache must stay warm and untouched.
		// The scene hasher resolves paths through this cache; staleness here is the shape that made
		// content-only notes ping-pong the server during sync.
		val after = sceneDatasource.getAllScenePaths()
		assertEquals(
			scansBefore,
			countingFs.listRecursivelyCount,
			"Content rewrite must not invalidate or re-scan the path cache"
		)
		assertEquals(before, after, "A content-only write must not change the cached path set")
		assertNotNull(
			sceneDatasource.resolveScenePathFromFilesystem(sceneItem.id),
			"Scene must remain resolvable from the warm cache after a content rewrite"
		)
	}

	@Test
	fun `moveScene keeps the cache in step`() = runTest {
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
		assertEquals(
			targetPath.toOkioPath(),
			sceneDatasource.resolveScenePathFromFilesystem(sourceId)?.toOkioPath(),
			"Moved scene must resolve to its new path",
		)
	}

	@Test
	fun `moving a scene to a new id re-indexes it`() = runTest {
		// reIdScene moves a scene to a filename carrying a different id; the cached id index has to
		// retire the old id rather than keep pointing it at the moved file.
		val oldId = 1
		val newId = 997
		val sourcePath = sceneDatasource.resolveScenePathFromFilesystem(oldId)
		assertNotNull(sourcePath)

		val targetPath = sourcePath.toOkioPath().parent!!
			.div("1~Re-IDed~$newId.md").toHPath()
		sceneDatasource.moveScene(sourcePath, targetPath)

		assertEquals(
			targetPath.toOkioPath(),
			sceneDatasource.resolveScenePathFromFilesystem(newId)?.toOkioPath(),
			"Scene must resolve under its new id",
		)
		assertEquals(
			null,
			sceneDatasource.resolveScenePathFromFilesystem(oldId),
			"The retired id must no longer resolve",
		)
	}

	@Test
	fun `moving one of two files sharing a scene id keeps the winner resolvable`() = runTest {
		// Duplicate ids on disk are tolerated, and the id resolves to the name-first of them. An
		// incremental cache update cannot maintain that, so a move must fall back to a re-scan.
		val sharedId = 1
		val winner = sceneDatasource.resolveScenePathFromFilesystem(sharedId)
		assertNotNull(winner)
		val loser = sceneDatasource.getSceneDirectory().toOkioPath()
			.div("9~Duplicate~$sharedId.md").toHPath()
		sceneDatasource.createNewGroup(loser)
		assertEquals(
			winner.toOkioPath(),
			sceneDatasource.resolveScenePathFromFilesystem(sharedId)?.toOkioPath(),
			"The name-first path owns the id",
		)

		val renamedLoser = sceneDatasource.getSceneDirectory().toOkioPath()
			.div("09~Duplicate~$sharedId.md").toHPath()
		sceneDatasource.moveScene(loser, renamedLoser)

		assertEquals(
			winner.toOkioPath(),
			sceneDatasource.resolveScenePathFromFilesystem(sharedId)?.toOkioPath(),
			"Moving the duplicate must not steal the id from the winner",
		)
	}

	@Test
	fun `countScenes stays correct without re-listing the directory`() = runTest {
		val sceneDir = sceneDatasource.getSceneDirectory()
		val before = sceneDatasource.countScenes(sceneDir)
		val listsBefore = countingFs.listCount

		val newId = 998
		val newPath = sceneDir.toOkioPath().div("6~Counted~$newId.md").toHPath()
		sceneDatasource.createNewGroup(newPath)

		assertEquals(before + 1, sceneDatasource.countScenes(sceneDir), "New scene must be counted")
		assertEquals(
			listsBefore,
			countingFs.listCount,
			"Counting children must come from the cached scan, not a fresh directory listing",
		)
	}
}
