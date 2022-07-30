package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.tree.Tree
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import kotlin.test.*

class TreeTest {

    private fun testTree(): Tree<String> {
        val root = TreeNode("0")

        val a1 = TreeNode("1a")
        val b1 = TreeNode("1b")
        root.addChild(a1)
        root.addChild(b1)

        val a2 = TreeNode("2a")
        a1.addChild(a2)

        val tree = Tree<String>()
        tree.setRoot(root)

        return tree
    }

    @Test
    fun `Tree Print`() {
        val tree = testTree()
        tree.print()
    }

    @Test
    fun `Tree Hashcode`() {
        val tree = testTree()
        val hashcode = tree.hashCode()
        println("Hashcode: $hashcode")
    }

    @Test
    fun `Tree Equals`() {
        val tree1 = testTree()
        val tree2 = testTree()

        assertEquals(tree1, tree2)

        val c1 = TreeNode("1c")
        tree2.root().addChild(c1)

        assertNotEquals(tree1, tree2)
    }

    @Test
    fun `Tree Iterate`() {
        val tree = testTree()
        val it = tree.iterator()

        assertTrue(it.hasNext())
        assertEquals("1a", it.next())

        assertTrue(it.hasNext())
        assertEquals("2a", it.next())

        assertTrue(it.hasNext())
        assertEquals("1b", it.next())

        assertFalse(it.hasNext())
    }

    @Test
    fun `Tree Find or Null`() {
        val tree = testTree()

        var foundNode = tree.findOrNull { it == "1a" }
        assertNotNull(foundNode)
        assertEquals("1a", foundNode.value)

        foundNode = tree.findOrNull { it == "2a" }
        assertNotNull(foundNode)
        assertEquals("2a", foundNode.value)

        foundNode = tree.findOrNull { it == "3a" }
        assertNull(foundNode)
    }

    @Test
    fun `Tree total children`() {
        val tree = testTree()
        assertEquals(3, tree.numChildrenRecursive())
    }

    @Test
    fun `Tree to Immutable Child Count`() {
        val tree = testTree()
        val imTree = tree.toImmutableTree()

        assertEquals(tree.numChildrenRecursive(), imTree.totalChildren)
    }

    @Test
    fun `Tree to Immutable Values`() {
        val tree = testTree()
        val imTree = tree.toImmutableTree()

        val treeIt = tree.iterator()
        val imTreeIt = imTree.iterator()

        while (treeIt.hasNext()) {
            val a = treeIt.next()
            val b = imTreeIt.next()
            assertEquals(a, b.value)
        }
    }
}