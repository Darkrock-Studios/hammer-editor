package repositories.sceneeditor

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.computeMoveRequest
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.validMoveDestinations
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Exercises [computeMoveRequest] end to end: the request it builds is fed through the real
 * [com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository.moveScene]
 * and the resulting sibling order is asserted.
 */
class SceneMoveUtilsTest : SceneRepositoryTestBase() {

	private fun immutableTree(): ImmutableTree<SceneItem> = repo.getSceneTree()

	private fun sceneItem(id: Int): SceneItem = immutableTree().findBy { it.id == id }!!.value

	private fun childIdsOf(parentId: Int): List<Int> =
		immutableTree().findBy { it.id == parentId }!!.children.map { it.value.id }

	private suspend fun moveTo(itemId: Int, destParentId: Int, destIndex: Int) {
		val request =
			computeMoveRequest(immutableTree(), sceneItem(itemId), destParentId, destIndex)
		assertNotNull(request, "Expected a valid MoveRequest")
		repo.moveScene(request)
	}

	@Test
	fun `Same parent, move down`() = runTest {
		moveTo(itemId = 1, destParentId = 0, destIndex = 2)
		assertEquals(listOf(2, 6, 1, 7), childIdsOf(0))
	}

	@Test
	fun `Same parent, move up`() = runTest {
		moveTo(itemId = 6, destParentId = 0, destIndex = 0)
		assertEquals(listOf(6, 1, 2, 7), childIdsOf(0))
	}

	@Test
	fun `Same parent, move to last`() = runTest {
		moveTo(itemId = 1, destParentId = 0, destIndex = 3)
		assertEquals(listOf(2, 6, 7, 1), childIdsOf(0))
	}

	@Test
	fun `Same parent, no-op position`() = runTest {
		moveTo(itemId = 6, destParentId = 0, destIndex = 2)
		assertEquals(listOf(1, 2, 6, 7), childIdsOf(0))
	}

	@Test
	fun `Same parent, index beyond range is coerced to last`() = runTest {
		moveTo(itemId = 1, destParentId = 0, destIndex = 99)
		assertEquals(listOf(2, 6, 7, 1), childIdsOf(0))
	}

	@Test
	fun `Cross parent, into group at first position`() = runTest {
		moveTo(itemId = 6, destParentId = 2, destIndex = 0)
		assertEquals(listOf(6, 3, 4, 5), childIdsOf(2))
		assertEquals(listOf(1, 2, 7), childIdsOf(0))
	}

	@Test
	fun `Cross parent, into group at middle position`() = runTest {
		moveTo(itemId = 6, destParentId = 2, destIndex = 1)
		assertEquals(listOf(3, 6, 4, 5), childIdsOf(2))
	}

	@Test
	fun `Cross parent, append to group`() = runTest {
		moveTo(itemId = 1, destParentId = 2, destIndex = 3)
		assertEquals(listOf(3, 4, 5, 1), childIdsOf(2))
	}

	@Test
	fun `Cross parent, out of group to root`() = runTest {
		moveTo(itemId = 4, destParentId = 0, destIndex = 1)
		assertEquals(listOf(1, 4, 2, 6, 7), childIdsOf(0))
		assertEquals(listOf(3, 5), childIdsOf(2))
	}

	@Test
	fun `Move into empty group`() = runTest {
		val group = repo.createGroup(null, "Empty Group")
		assertNotNull(group)

		moveTo(itemId = 1, destParentId = group.id, destIndex = 0)
		assertEquals(listOf(1), childIdsOf(group.id))
	}

	@Test
	fun `Destination is a scene, not a group`() {
		val request =
			computeMoveRequest(immutableTree(), sceneItem(1), destParentId = 6, destIndex = 0)
		assertNull(request)
	}

	@Test
	fun `Destination is the item itself`() {
		val request =
			computeMoveRequest(immutableTree(), sceneItem(2), destParentId = 2, destIndex = 0)
		assertNull(request)
	}

	@Test
	fun `Destination is inside the item's own subtree`() = runTest {
		val innerGroup = repo.createGroup(sceneItem(2), "Inner Group")
		assertNotNull(innerGroup)

		val request =
			computeMoveRequest(
				immutableTree(),
				sceneItem(2),
				destParentId = innerGroup.id,
				destIndex = 0
			)
		assertNull(request)
	}

	@Test
	fun `Valid destinations exclude self and own subtree`() = runTest {
		val innerGroup = repo.createGroup(sceneItem(2), "Inner Group")
		assertNotNull(innerGroup)

		val forGroup = validMoveDestinations(immutableTree(), sceneItem(2)).map { it.value.id }
		assertEquals(listOf(0), forGroup)

		val forScene = validMoveDestinations(immutableTree(), sceneItem(1)).map { it.value.id }
		assertEquals(listOf(0, 2, innerGroup.id), forScene)
	}
}
