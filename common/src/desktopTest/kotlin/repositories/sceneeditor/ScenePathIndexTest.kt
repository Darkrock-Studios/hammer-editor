package repositories.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.ScenePathIndex
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the changes [ScenePathIndex] deliberately refuses to describe incrementally, each of which
 * must fall back to a re-scan. The observable cache behaviour on top of these is covered through
 * the datasource in [SceneDatasourceCacheTest].
 */
class ScenePathIndexTest {

	private val sceneDir = "/proj/.scenes".toPath()

	private fun scenePath(name: String): HPath = sceneDir.div(name).toHPath()

	private fun groupChild(group: String, name: String): HPath =
		sceneDir.div(group).div(name).toHPath()

	private fun archivedPath(name: String): HPath =
		sceneDir.div(SceneDatasource.ARCHIVED_DIRECTORY).div(name).toHPath()

	private class CountingScan(private val paths: List<HPath>) : () -> List<HPath> {
		var calls = 0
			private set

		override fun invoke(): List<HPath> {
			calls++
			return paths.sortedBy { it.name }
		}
	}

	@Test
	fun `moving a group that has children re-scans rather than splicing`() {
		// The index is a flat list, so it cannot re-root the subtree a moved directory carries.
		val group = scenePath("1~Chapter~1")
		val child = groupChild("1~Chapter~1", "1~Scene~2.md")
		val scan = CountingScan(listOf(group, child))
		val index = ScenePathIndex(scan)
		index.paths()
		index.childCount(group)
		val scansBefore = scan.calls

		index.onMoved(group, scenePath("01~Chapter~1"))

		index.paths()
		assertEquals(scansBefore + 1, scan.calls, "Moving a group with children must force a re-scan")
	}

	@Test
	fun `re-IDing onto an occupied filename leaves that path listed once`() {
		// atomicMove replaces whatever held the target name, so the displaced file leaves the index
		// with it, and the id encoded in that name now belongs to the file that moved there.
		val source = scenePath("1~One~1.md")
		val target = scenePath("2~Two~2.md")
		val scan = CountingScan(listOf(source, target))
		val index = ScenePathIndex(scan)
		index.paths()

		index.onMoved(source, target)

		val paths = index.paths()
		assertEquals(1, paths.size, "The displaced file left with the move")
		assertEquals(target.toOkioPath(), paths.single().toOkioPath(), "Only the target remains")
		assertEquals(target.toOkioPath(), index.pathFor(2)?.toOkioPath(), "The target name owns id 2")
		assertNull(index.pathFor(1), "No file claims the retired id")
	}

	@Test
	fun `a move whose source was never indexed re-scans`() {
		// Unarchiving moves a file in from outside this index's scope.
		val known = scenePath("1~One~1.md")
		val scan = CountingScan(listOf(known))
		val index = ScenePathIndex(scan)
		index.paths()
		val scansBefore = scan.calls

		index.onMoved(archivedPath("Two~2.md"), scenePath("2~Two~2.md"))

		index.paths()
		assertEquals(scansBefore + 1, scan.calls, "An unindexed source must force a re-scan")
	}

	@Test
	fun `archiving drops the scene from the index without re-scanning`() {
		val staying = scenePath("1~One~1.md")
		val leaving = scenePath("2~Two~2.md")
		val scan = CountingScan(listOf(staying, leaving))
		val index = ScenePathIndex(scan)
		index.paths()
		index.pathFor(2)
		index.childCount(sceneDir.toHPath())
		val scansBefore = scan.calls

		index.onMoved(leaving, archivedPath("Two~2.md"))

		assertEquals(listOf(staying.toOkioPath()), index.paths().map { it.toOkioPath() })
		assertNull(index.pathFor(2), "An archived scene is outside the index")
		assertEquals(1, index.childCount(sceneDir.toHPath()), "The archived scene left its directory")
		assertEquals(scansBefore, scan.calls, "Archiving is describable, so it must not re-scan")
	}
}
