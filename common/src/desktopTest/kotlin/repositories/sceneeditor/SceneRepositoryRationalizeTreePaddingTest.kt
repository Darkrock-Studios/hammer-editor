package repositories.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.runBlocking
import okio.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A sync that nests items can drop a directory's child count across a digit boundary
 * (10 -> 9), changing the zero-padding width of every sibling's order number. The whole
 * rationalize pass must agree on one padding width even though moves mutate the disk
 * mid-pass. Reproduces GitHub-reported sync failure at the 9/10 boundary.
 */
class SceneRepositoryRationalizeTreePaddingTest : SceneRepositoryTestBase() {

	private fun writeScene(dir: Path, name: String) {
		ffs.write(dir.div(name)) { writeUtf8("test content") }
	}

	@Test
	fun `Rationalize tree when root falls below 10 items mid-pass`() = runBlocking {
		// 11 root items on disk: two-digit order padding
		val scenesDir = projectPath.toOkioPath().div(SceneDatasource.SCENE_DIRECTORY)
		ffs.deleteRecursively(scenesDir)
		ffs.createDirectories(scenesDir)

		for (i in 0..5) {
			writeScene(scenesDir, "0$i~Scene ${i + 1}~${i + 1}.md")
		}
		ffs.createDirectory(scenesDir.div("06~First Book~9"))
		ffs.createDirectory(scenesDir.div("07~Beauty and the Beast~14"))
		writeScene(scenesDir, "08~Dangerous Wish~10.md")
		writeScene(scenesDir, "09~Scene D~13.md")
		writeScene(scenesDir, "10~Scene E~11.md")

		sceneDatasource = SceneDatasource(projectDef, ffs)
		repo = createRepository()
		repo.initializeSceneEditor()

		// Simulate downloaded server entities: group 14 nests under group 9,
		// scenes 10 and 13 nest under group 14. Root falls from 11 items to 8.
		val tree = repo.rawTree
		val root = tree.root()
		val group9 = tree.find { it.id == 9 }
		val group14 = tree.find { it.id == 14 }
		val scene10 = tree.find { it.id == 10 }
		val scene13 = tree.find { it.id == 13 }
		val scene11 = tree.find { it.id == 11 }

		root.removeChild(group14)
		root.removeChild(scene10)
		root.removeChild(scene13)
		group9.addChild(group14)
		group14.addChild(scene10)
		group14.addChild(scene13)

		group14.value = group14.value.copy(order = 0)
		scene10.value = scene10.value.copy(order = 0)
		scene13.value = scene13.value.copy(order = 1)
		scene11.value = scene11.value.copy(order = 7)

		repo.rationalizeTree()

		// Root now has 8 children, so every root-level name uses single-digit padding
		val group9Dir = scenesDir.div("6~First Book~9")
		val group14Dir = group9Dir.div("0~Beauty and the Beast~14")
		assertTrue(ffs.exists(group9Dir))
		assertTrue(ffs.exists(group14Dir))
		assertTrue(ffs.exists(group14Dir.div("0~Dangerous Wish~10.md")))
		assertTrue(ffs.exists(group14Dir.div("1~Scene D~13.md")))
		assertTrue(ffs.exists(scenesDir.div("7~Scene E~11.md")))
		for (i in 0..5) {
			assertTrue(ffs.exists(scenesDir.div("$i~Scene ${i + 1}~${i + 1}.md")))
		}
		assertFalse(ffs.exists(scenesDir.div("06~First Book~9")))

		// Disk and computed paths must agree for every scene after the pass
		tree.forEach { node ->
			if (node.value.isRootScene) return@forEach
			assertTrue(
				ffs.exists(repo.getSceneFilePath(node.value.id).toOkioPath()),
				"Scene ${node.value.id} not found at its computed path"
			)
		}
	}

	@Test
	fun `Rationalize tree pads from disk-present children when a tree child is missing on disk`() = runBlocking {
		// 9 scenes on disk: single-digit order padding
		val scenesDir = projectPath.toOkioPath().div(SceneDatasource.SCENE_DIRECTORY)
		ffs.deleteRecursively(scenesDir)
		ffs.createDirectories(scenesDir)

		for (i in 0..8) {
			writeScene(scenesDir, "$i~Scene ${i + 1}~${i + 1}.md")
		}

		sceneDatasource = SceneDatasource(projectDef, ffs)
		repo = createRepository()
		repo.initializeSceneEditor()

		// A 10th tree child with no file on disk (e.g. a failed download) must not push
		// padding to two digits, or computed paths diverge from disk afterwards
		val tree = repo.rawTree
		val phantom = SceneItem(projectDef, SceneItem.Type.Scene, 99, "Phantom", 9)
		tree.root().addChild(TreeNode(phantom))

		repo.rationalizeTree()

		for (i in 0..8) {
			val path = scenesDir.div("$i~Scene ${i + 1}~${i + 1}.md")
			assertTrue(ffs.exists(path), "Scene file unexpectedly renamed: $path")
		}
		tree.forEach { node ->
			if (node.value.isRootScene || node.value.id == phantom.id) return@forEach
			assertTrue(
				ffs.exists(repo.getSceneFilePath(node.value.id).toOkioPath()),
				"Scene ${node.value.id} not found at its computed path"
			)
		}
	}
}
