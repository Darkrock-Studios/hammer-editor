package repositories.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.ScenePathIndex
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the two answers the index gives that its callers cannot check for themselves: which of two
 * files claiming one scene id owns it, and whether a directory is empty or simply unseen. The rest
 * of its behaviour is covered through the datasource in [SceneDatasourceCacheTest].
 */
class ScenePathIndexTest {

	private val sceneDir = "/proj/.scenes".toPath()

	private fun scenePath(name: String): HPath = sceneDir.div(name).toHPath()

	@Test
	fun `the name-first file owns a shared scene id`() {
		val incumbent = scenePath("1~Alpha~5.md")
		val index = ScenePathIndex { listOf(incumbent) }
		index.paths()

		// A later arrival sorting before the incumbent takes the id; one sorting after does not.
		index.onCreated(scenePath("0~Aardvark~5.md"))
		assertEquals(
			scenePath("0~Aardvark~5.md").toOkioPath(),
			index.pathFor(5)?.toOkioPath(),
			"The name-first duplicate owns the id",
		)

		index.onCreated(scenePath("9~Omega~5.md"))
		assertEquals(
			scenePath("0~Aardvark~5.md").toOkioPath(),
			index.pathFor(5)?.toOkioPath(),
			"A later-sorting duplicate does not take it",
		)
	}

	@Test
	fun `an unseen directory reports unknown rather than empty`() {
		// countScenePathsIn reads the disk for these, because order numbers derive from the count
		// and treating an unseen directory as empty would mis-number a real one.
		val index = ScenePathIndex { listOf(scenePath("1~One~1.md")) }
		index.paths()

		assertEquals(1, index.childCountOrNull(sceneDir.toHPath()), "A scanned directory is counted")
		assertNull(index.childCountOrNull(scenePath("7~Unseen~7")), "An unseen one is unknown")
	}

	@Test
	fun `a created file is absorbed without re-scanning`() {
		var scans = 0
		val existing = scenePath("1~One~1.md")
		val index = ScenePathIndex { scans++; listOf(existing) }
		index.paths()
		index.pathFor(1)
		index.childCountOrNull(sceneDir.toHPath())
		val scansBefore = scans

		index.onCreated(scenePath("2~Two~2.md"))

		assertEquals(2, index.paths().size, "The new file is listed")
		assertEquals(scenePath("2~Two~2.md").toOkioPath(), index.pathFor(2)?.toOkioPath())
		assertEquals(2, index.childCountOrNull(sceneDir.toHPath()), "It is counted in its directory")
		assertEquals(scansBefore, scans, "Creating a file must not cost a re-scan")
	}
}
