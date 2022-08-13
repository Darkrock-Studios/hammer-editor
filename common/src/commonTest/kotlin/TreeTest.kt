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
        val it: Iterator<TreeNode<String>> = tree.iterator()

        assertTrue(it.hasNext())
        assertEquals("0", it.next().value)

        assertTrue(it.hasNext())
        assertEquals("1a", it.next().value)

        assertTrue(it.hasNext())
        assertEquals("2a", it.next().value)

        assertTrue(it.hasNext())
        assertEquals("1b", it.next().value)

        assertFalse(it.hasNext())
    }

    @Test
    fun `Tree By Index`() {
        val tree = testTree()

        assertEquals("0", tree[0].value)
        assertEquals("1a", tree[1].value)
        assertEquals("2a", tree[2].value)
        assertEquals("1b", tree[3].value)
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
            assertEquals(a.value, b.value)
        }
    }

    @Test
    fun `Immutable Tree index validation`() {
        val tree = testTree()
        val imTree = tree.toImmutableTree()

        imTree.print()

        var curIndex = 0
        for ((ii, node) in imTree.withIndex()) {
            println("$ii -- " + node.value)
            assertEquals(ii, node.index, "${node.value}")
            ++curIndex
        }
    }

    @Test
    fun `Immutable Tree get by index`() {
        val tree = testTree()
        val imTree = tree.toImmutableTree()

        assertEquals("0", imTree[0].value)
        assertEquals("1a", imTree[1].value)
        assertEquals("2a", imTree[2].value)
        assertEquals("1b", imTree[3].value)
    }
}