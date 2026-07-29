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
 * Covers structural changes whose correct answer is hard to stage through [SceneDatasource]: moves
 * involving directories, occupied target names and duplicate ids. Each asserts what the index
 * reports afterwards, not whether it got there by splicing or re-scanning.
 */
class ScenePathIndexTest {

	private val sceneDir = "/proj/.scenes".toPath()

	private fun scenePath(name: String): HPath = sceneDir.div(name).toHPath()

	private fun groupChild(group: String, name: String): HPath =
		sceneDir.div(group).div(name).toHPath()

	private fun archivedPath(name: String): HPath =
		sceneDir.div(SceneDatasource.ARCHIVED_DIRECTORY).div(name).toHPath()

	/** Stands in for the on-disk scan, so a test can move files by rewriting what a re-scan sees. */
	private class FakeScan(paths: List<HPath>) : () -> List<HPath> {
		var onDisk: List<HPath> = paths
		var calls = 0
			private set

		fun move(from: HPath, to: HPath) {
			onDisk = onDisk.filterNot {
				it.toOkioPath() == from.toOkioPath() || it.toOkioPath() == to.toOkioPath()
			} + to
		}

		override fun invoke(): List<HPath> {
			calls++
			return onDisk.sortedBy { it.name }
		}
	}

	@Test
	fun `a renamed group still resolves, and so do the scenes inside it`() {
		val group = scenePath("1~Chapter~1")
		val child = groupChild("1~Chapter~1", "1~Scene~2.md")
		val renamed = scenePath("01~Chapter~1")
		val movedChild = groupChild("01~Chapter~1", "1~Scene~2.md")
		val scan = FakeScan(listOf(group, child))
		val index = ScenePathIndex(scan)
		index.paths()
		index.childCountOrNull(group)

		scan.onDisk = listOf(renamed, movedChild)
		index.onMoved(group, renamed)

		assertEquals(renamed.toOkioPath(), index.pathFor(1)?.toOkioPath(), "Group resolves at its new name")
		assertEquals(
			movedChild.toOkioPath(),
			index.pathFor(2)?.toOkioPath(),
			"A scene inside a renamed group moves with it",
		)
		assertEquals(1, index.childCountOrNull(renamed), "The child is counted under the new name")
	}

	@Test
	fun `re-IDing onto an occupied filename leaves that path listed once`() {
		// atomicMove replaces whatever held the target name, so the displaced file is gone and the
		// id encoded in that name belongs to the file that moved there.
		val source = scenePath("1~One~1.md")
		val target = scenePath("2~Two~2.md")
		val scan = FakeScan(listOf(source, target))
		val index = ScenePathIndex(scan)
		index.paths()

		scan.move(source, target)
		index.onMoved(source, target)

		val paths = index.paths()
		assertEquals(1, paths.size, "The displaced file left with the move")
		assertEquals(target.toOkioPath(), paths.single().toOkioPath(), "Only the target remains")
		assertEquals(target.toOkioPath(), index.pathFor(2)?.toOkioPath(), "The target name owns id 2")
		assertNull(index.pathFor(1), "No file claims the retired id")
	}

	@Test
	fun `a move that duplicates a scene id resolves to the name-first file`() {
		// Two files claiming one id is tolerated on disk and settled by name order, so the index
		// must agree with a full scan rather than let the newcomer take the id.
		val incumbent = scenePath("1~Alpha~5.md")
		val source = scenePath("9~Beta~7.md")
		val target = scenePath("9~Beta~5.md")
		val scan = FakeScan(listOf(incumbent, source))
		val index = ScenePathIndex(scan)
		index.paths()
		assertEquals(incumbent.toOkioPath(), index.pathFor(5)?.toOkioPath())

		scan.move(source, target)
		index.onMoved(source, target)

		assertEquals(
			incumbent.toOkioPath(),
			index.pathFor(5)?.toOkioPath(),
			"The name-first file keeps the id it shares",
		)
	}

	@Test
	fun `an unarchived scene becomes resolvable again`() {
		val staying = scenePath("1~One~1.md")
		val archived = archivedPath("Two~2.md")
		val restored = scenePath("2~Two~2.md")
		val scan = FakeScan(listOf(staying))
		val index = ScenePathIndex(scan)
		index.paths()
		assertNull(index.pathFor(2), "Archived scenes are outside the index")

		scan.onDisk = listOf(staying, restored)
		index.onMoved(archived, restored)

		assertEquals(restored.toOkioPath(), index.pathFor(2)?.toOkioPath(), "Unarchived scene resolves")
		assertEquals(2, index.childCountOrNull(sceneDir.toHPath()), "It is counted in its directory")
	}

	@Test
	fun `archiving removes the scene from the index without re-reading the disk`() {
		// The perf contract this index exists for: describable changes are absorbed, not re-scanned.
		val staying = scenePath("1~One~1.md")
		val leaving = scenePath("2~Two~2.md")
		val scan = FakeScan(listOf(staying, leaving))
		val index = ScenePathIndex(scan)
		index.paths()
		index.pathFor(2)
		index.childCountOrNull(sceneDir.toHPath())
		val scansBefore = scan.calls

		index.onMoved(leaving, archivedPath("Two~2.md"))

		assertEquals(listOf(staying.toOkioPath()), index.paths().map { it.toOkioPath() })
		assertNull(index.pathFor(2), "An archived scene is outside the index")
		assertEquals(1, index.childCountOrNull(sceneDir.toHPath()), "It left its directory")
		assertEquals(scansBefore, scan.calls, "Archiving is describable, so it is absorbed")
	}

	@Test
	fun `a directory the scan never reached reports an unknown count`() {
		val scan = FakeScan(listOf(scenePath("1~One~1.md")))
		val index = ScenePathIndex(scan)
		index.paths()

		assertNull(
			index.childCountOrNull(scenePath("7~Empty~7")),
			"Unknown must be distinguishable from empty so the caller can check the disk",
		)
	}
}
