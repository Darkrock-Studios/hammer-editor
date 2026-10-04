package repositories.tree

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ChapterScenes
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.data.tree.collectChapters
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneChaptersTest {

	private val projectDef = ProjectDef("Test", HPath("/projects/Test", "Test", false))

	private fun item(id: Int, type: SceneItem.Type) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = "Item $id", order = id)

	private fun scene(id: Int) = TreeNode(item(id, SceneItem.Type.Scene))

	private fun group(id: Int, vararg children: TreeNode<SceneItem>) =
		TreeNode(item(id, SceneItem.Type.Group)).apply { children.forEach { addChild(it) } }

	private fun tree(vararg topLevel: TreeNode<SceneItem>): ImmutableTree<SceneItem> {
		val root = TreeNode(item(SceneItem.ROOT_ID, SceneItem.Type.Root))
		topLevel.forEach { root.addChild(it) }
		return Tree<SceneItem>().apply { setRoot(root) }.toImmutableTree()
	}

	@Test
	fun `a top-level scene is a one-scene chapter`() {
		val chapters = tree(scene(1), scene(2)).collectChapters()

		assertEquals(listOf(1, 2), chapters.map { it.chapter.id })
		assertEquals(listOf(listOf(1), listOf(2)), chapters.map { c -> c.scenes.map { it.id } })
	}

	@Test
	fun `a group is flattened depth-first to its descendant scenes`() {
		val chapters = tree(
			group(10, scene(11), group(12, scene(13), scene(14)), scene(15)),
			scene(20),
		).collectChapters()

		assertEquals(listOf(10, 20), chapters.map { it.chapter.id })
		assertEquals(listOf(11, 13, 14, 15), chapters[0].scenes.map { it.id })
		assertTrue(chapters[0].scenes.all { it.type == SceneItem.Type.Scene })
	}

	@Test
	fun `an empty group is still a chapter`() {
		val chapters = tree(group(10), group(20, group(21))).collectChapters()

		assertEquals(
			listOf(emptyList(), emptyList<SceneItem>()),
			chapters.map(ChapterScenes::scenes),
		)
	}

	@Test
	fun `an empty tree has no chapters`() {
		assertTrue(tree().collectChapters().isEmpty())
	}
}
