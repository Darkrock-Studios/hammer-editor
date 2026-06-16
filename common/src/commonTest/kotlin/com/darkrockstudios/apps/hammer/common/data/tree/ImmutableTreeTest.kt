package com.darkrockstudios.apps.hammer.common.data.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImmutableTreeTest {

	/**
	 *         0
	 *        / \
	 *      1a   1b
	 *     /  \    \
	 *   2a   2b   2c
	 *
	 * Depth-first global indices: 0, 1a=1, 2a=2, 2b=3, 1b=4, 2c=5
	 */
	private fun testTree(): ImmutableTree<String> {
		val root = TreeNode("0")

		val a1 = TreeNode("1a")
		val b1 = TreeNode("1b")
		root.addChild(a1)
		root.addChild(b1)

		a1.addChild(TreeNode("2a"))
		a1.addChild(TreeNode("2b"))
		b1.addChild(TreeNode("2c"))

		val tree = Tree<String>()
		tree.setRoot(root)
		return tree.toImmutableTree()
	}

	@Test
	fun `totalNodes counts root plus all descendants`() {
		val tree = testTree()
		assertEquals(5, tree.totalChildren)
		assertEquals(6, tree.totalNodes)
	}

	@Test
	fun `get by index returns the node at that global index`() {
		val tree = testTree()
		assertEquals("0", tree[0].value)
		assertEquals("1a", tree[1].value)
		assertEquals("2a", tree[2].value)
		assertEquals("2b", tree[3].value)
		assertEquals("1b", tree[4].value)
		assertEquals("2c", tree[5].value)
	}

	@Test
	fun `get with out of range index throws`() {
		val tree = testTree()
		assertFailsWith<IndexOutOfBoundsException> { tree[6] }
		assertFailsWith<IndexOutOfBoundsException> { tree[-1] }
	}

	@Test
	fun `indexOf node returns its iteration index`() {
		val tree = testTree()
		assertEquals(0, tree.indexOf(tree[0]))
		assertEquals(3, tree.indexOf(tree[3]))
		assertEquals(5, tree.indexOf(tree[5]))
	}

	@Test
	fun `indexOf predicate returns matching index or minus one`() {
		val tree = testTree()
		assertEquals(2, tree.indexOf { it == "2a" })
		assertEquals(4, tree.indexOf { it == "1b" })
		assertEquals(-1, tree.indexOf { it == "nope" })
	}

	@Test
	fun `findBy returns matching node or null`() {
		val tree = testTree()
		val found = tree.findBy { it == "2c" }
		assertEquals("2c", found?.value)
		assertEquals(5, found?.index)

		assertNull(tree.findBy { it == "nope" })
	}

	@Test
	fun `isAncestorOf is true for direct and transitive ancestors`() {
		val tree = testTree()
		// root and 1a are ancestors of 2a (index 2)
		assertTrue(tree.isAncestorOf(needleIndex = 0, leafIndex = 2))
		assertTrue(tree.isAncestorOf(needleIndex = 1, leafIndex = 2))
	}

	@Test
	fun `isAncestorOf is false for non-ancestors and for self`() {
		val tree = testTree()
		// 1b (index 4) is in a different branch from 2a (index 2)
		assertFalse(tree.isAncestorOf(needleIndex = 4, leafIndex = 2))
		// a node is not its own ancestor
		assertFalse(tree.isAncestorOf(needleIndex = 2, leafIndex = 2))
	}

	@Test
	fun `getBranch includes the full path from root to leaf`() {
		val tree = testTree()
		val branch = tree.getBranch(leafIndex = 2, excludeLeaf = false)
		assertEquals(listOf("0", "1a", "2a"), branch.map { it.value })
	}

	@Test
	fun `getBranch can exclude the leaf`() {
		val tree = testTree()
		val branch = tree.getBranch(leafIndex = 2, excludeLeaf = true)
		assertEquals(listOf("0", "1a"), branch.map { it.value })
	}

	@Test
	fun `getBranch of root is just the root`() {
		val tree = testTree()
		val branch = tree.getBranch(leafIndex = 0, excludeLeaf = false)
		assertEquals(listOf("0"), branch.map { it.value })
	}

	@Test
	fun `getCoordinatesFor root returns Root coordinates`() {
		val tree = testTree()
		val coords = tree.getCoordinatesFor(tree[0])
		assertEquals(NodeCoordinates.Root, coords)
		assertTrue(coords.isTreeRoot())
	}

	@Test
	fun `getCoordinatesFor child returns parent and local child index`() {
		val tree = testTree()
		// 2b is the second child (local index 1) of 1a (global index 1)
		val coords = tree.getCoordinatesFor(tree[3])
		assertEquals(3, coords.globalIndex)
		assertEquals(1, coords.parentIndex)
		assertEquals(1, coords.childLocalIndex)
		assertFalse(coords.isTreeRoot())
	}

	@Test
	fun `getByCoordinates round-trips with getCoordinatesFor`() {
		val tree = testTree()
		for (index in 0 until tree.totalNodes) {
			val node = tree[index]
			val coords = tree.getCoordinatesFor(node)
			assertEquals(node, tree.getByCoordinates(coords))
		}
	}

	@Test
	fun `getByCoordinates with Root returns the root`() {
		val tree = testTree()
		assertEquals(tree[0], tree.getByCoordinates(NodeCoordinates.Root))
	}

	@Test
	fun `list returns all nodes in depth-first order`() {
		val tree = testTree()
		assertEquals(
			listOf("0", "1a", "2a", "2b", "1b", "2c"),
			tree.list().map { it.value }
		)
	}

	@Test
	fun `iterator throws when exhausted`() {
		val tree = testTree()
		val iter = tree.iterator()
		repeat(tree.totalNodes) { iter.next() }
		assertFalse(iter.hasNext())
		assertFailsWith<NoSuchElementException> { iter.next() }
	}

	@Test
	fun `equal trees are equal and share a hashCode`() {
		val a = testTree()
		val b = testTree()
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
	}

	@Test
	fun `trees with different structure are not equal`() {
		val a = testTree()

		val root = TreeNode("0")
		root.addChild(TreeNode("1a"))
		val other = Tree<String>().apply { setRoot(root) }.toImmutableTree()

		assertNotEquals(a, other)
	}

	@Test
	fun `tree is not equal to a non-tree value`() {
		val tree = testTree()
		assertFalse(tree.equals("not a tree"))
	}
}
