package com.darkrockstudios.apps.hammer.common.tree

/**
 * A depth first tree, indexable as such:
 *           1
 *          / \
 *         2   6
 *        /|\   \
 *       3 4 5   7
 */
interface TreeData<T> : Iterable<TreeNode<T>> {
    fun findOrNull(predicate: (T) -> Boolean): TreeNode<T>?

    @Throws(NodeNotFound::class)
    fun find(predicate: (T) -> Boolean): TreeNode<T>
    fun findValueOrNull(predicate: (T) -> Boolean): T?

    @Throws(NodeNotFound::class)
    fun findValue(predicate: (T) -> Boolean): T
    fun getBranch(leaf: TreeNode<T>, excludeLeaf: Boolean = false): List<TreeNode<T>>
    fun getBranch(excludeLeaf: Boolean = false, predicate: (T) -> Boolean): List<TreeNode<T>>
    fun getBranchOrNull(excludeLeaf: Boolean, predicate: (T) -> Boolean): List<TreeNode<T>>?
    fun addChild(child: TreeNode<T>)
    fun insertChild(at: Int, child: TreeNode<T>)
    fun removeChild(child: TreeNode<T>): Boolean
    fun hasImmediateChild(target: TreeNode<T>): Boolean
    fun hasChildRecursive(target: TreeNode<T>): Boolean
    fun numChildrenRecursive(): Int

    fun toTreeValue(depth: Int, parentIndex: Int, yourIndex: Int): Pair<TreeValue<T>, Int>
    fun numChildrenImmedate(): Int
    fun print(depth: Int)
    fun indexOfChild(child: TreeNode<T>): Int
}

data class TreeNode<T>(
    var value: T,
    var parent: TreeNode<T>? = null,
    private val children: MutableList<TreeNode<T>> = mutableListOf()
) : TreeData<T> {

    operator fun get(index: Int): TreeNode<T> {
        return children[index]
    }

    fun localIndexOf(child: TreeNode<T>): Int = children.indexOf(child)

    override fun findOrNull(predicate: (T) -> Boolean): TreeNode<T>? {
        var foundNode: TreeNode<T>? = null
        if (predicate(value)) {
            foundNode = this
        } else {
            for (child in children) {
                foundNode = child.findOrNull(predicate)
                if (foundNode != null) break
            }
        }
        return foundNode
    }

    @Throws(NodeNotFound::class)
    override fun find(predicate: (T) -> Boolean): TreeNode<T> {
        return findOrNull(predicate) ?: throw NodeNotFound()
    }

    override fun findValueOrNull(predicate: (T) -> Boolean): T? {
        return findOrNull(predicate)?.value
    }

    @Throws(NodeNotFound::class)
    override fun findValue(predicate: (T) -> Boolean): T {
        return findValueOrNull(predicate) ?: throw NodeNotFound()
    }

    override fun getBranch(leaf: TreeNode<T>, excludeLeaf: Boolean): List<TreeNode<T>> {
        val branch = mutableListOf<TreeNode<T>>()

        if (!excludeLeaf) {
            branch.add(leaf)
        }

        var curParent = leaf.parent
        while (curParent != null) {
            branch.add(curParent)
            curParent = curParent.parent
        }

        branch.reverse()

        return branch
    }

    override fun getBranch(excludeLeaf: Boolean, predicate: (T) -> Boolean): List<TreeNode<T>> {
        val node = find(predicate)
        return getBranch(node, excludeLeaf)
    }

    override fun getBranchOrNull(
        excludeLeaf: Boolean,
        predicate: (T) -> Boolean
    ): List<TreeNode<T>>? {
        val node = findOrNull(predicate)
        return if (node != null) {
            getBranch(node, excludeLeaf)
        } else {
            null
        }
    }

    override fun addChild(child: TreeNode<T>) {
        child.parent?.removeChild(child)
        child.parent = this
        children.add(child)
    }

    override fun indexOfChild(child: TreeNode<T>): Int = children.indexOf(child)

    override fun insertChild(at: Int, child: TreeNode<T>) {
        child.parent?.removeChild(child)
        child.parent = this
        children.add(at, child)
    }

    override fun removeChild(child: TreeNode<T>): Boolean {
        child.parent = null
        return children.remove(child)
    }

    override fun hasImmediateChild(target: TreeNode<T>): Boolean = children.contains(target)

    override fun hasChildRecursive(target: TreeNode<T>): Boolean {
        var hasChild = hasImmediateChild(target)
        if (!hasChild) {
            for (child in children) {
                if (child.hasChildRecursive(target)) {
                    hasChild = true
                    break
                }
            }
        }
        return hasChild
    }

    override fun iterator(): Iterator<TreeNode<T>> = NodeIterator(this)

    fun children(): List<TreeNode<T>> = children

    private class NodeIterator<T>(private val node: TreeNode<T>) : Iterator<TreeNode<T>> {

        private var hasServedSelf: Boolean = false

        private var myChildrenIterator: Iterator<TreeNode<T>> = node.children.iterator()
        private var currentChildIterator: Iterator<TreeNode<T>>? = null

        override fun hasNext(): Boolean {
            return !hasServedSelf || myChildrenIterator.hasNext() || currentChildIterator?.hasNext() == true
        }

        private fun advanceChildAndGetFirst(): TreeNode<T> {
            val child: TreeNode<T> = myChildrenIterator.next()
            val newIterator = child.iterator()
            currentChildIterator = newIterator
            return newIterator.next()
        }

        override fun next(): TreeNode<T> {
            val currentIterator = currentChildIterator

            // Serve self
            return if (!hasServedSelf) {
                hasServedSelf = true
                return node
            }
            // Serve children Depth first
            else if (currentIterator != null && currentIterator.hasNext()) {
                return currentIterator.next()
            }
            // Now go breadth across your children
            else if (myChildrenIterator.hasNext()) {
                advanceChildAndGetFirst()
            } else {
                throw IllegalStateException("No children left")
            }
        }
    }

    override fun toTreeValue(
        depth: Int,
        parentIndex: Int,
        yourIndex: Int
    ): Pair<TreeValue<T>, Int> {
        val numChildren = numChildrenRecursive()

        val childValues = mutableListOf<TreeValue<T>>()
        var nextIndex = yourIndex + 1
        for (child in children) {
            val (childValue, lastIndex) = child.toTreeValue(
                depth = depth + 1,
                parentIndex = yourIndex,
                yourIndex = nextIndex
            )
            childValues.add(childValue)
            nextIndex = lastIndex
        }

        return Pair(
            TreeValue(value, yourIndex, parentIndex, childValues, depth, numChildren),
            nextIndex
        )
    }

    override fun numChildrenImmedate(): Int = children.size

    override fun numChildrenRecursive(): Int {
        var numChildren = numChildrenImmedate()

        for (child in children) {
            numChildren += child.numChildrenRecursive()
        }

        return numChildren
    }

    override fun hashCode(): Int {
        return children.fold(value.hashCode()) { acc, child -> (43 * acc) + child.hashCode() }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is TreeNode<*>) {
            var isEqual = other.value == value && children.size == other.children.size
            if (isEqual) {

                val otherChildren = other.children.toList()
                for ((ii, c1) in children.withIndex()) {
                    val c2 = otherChildren[ii]
                    if (c1 != c2) {
                        isEqual = false
                        break
                    }
                }
            }
            isEqual
        } else {
            false
        }
    }

    override fun print(depth: Int) {
        var str = ""
        repeat(depth) {
            str += " "
        }
        str += "* " + value.toString()
        println(str)

        children.forEach { it.print(depth + 1) }
    }

    override fun toString(): String {
        return "TreeNode:\nValue: $value\nParent: ${parent?.value}\nChildren: ${children.size}"
    }
}

class Tree<T> : TreeData<T> {
    private lateinit var treeRoot: TreeNode<T>

    fun setRoot(node: TreeNode<T>) {
        treeRoot = node
    }

    fun root() = treeRoot

    /*
    // Eh... this does nothing
    fun move(target: TreeNode<T>, from: TreeNode<T>, to: TreeNode<T>): Boolean {
        return if (from.hasImmediateChild(target)) {
            from.removeChild(target)
            to.addChild(target)
            true
        } else {
            false
        }
    }
    */

    override fun iterator() = treeRoot.iterator()
    override fun findOrNull(predicate: (T) -> Boolean) = treeRoot.findOrNull(predicate)
    override fun find(predicate: (T) -> Boolean) = treeRoot.find(predicate)
    override fun findValueOrNull(predicate: (T) -> Boolean) = treeRoot.findValueOrNull(predicate)
    override fun findValue(predicate: (T) -> Boolean) = treeRoot.findValue(predicate)
    override fun getBranch(leaf: TreeNode<T>, excludeLeaf: Boolean) = treeRoot.getBranch(leaf)
    override fun getBranch(excludeLeaf: Boolean, predicate: (T) -> Boolean) =
        treeRoot.getBranch(excludeLeaf, predicate)

    override fun getBranchOrNull(
        excludeLeaf: Boolean,
        predicate: (T) -> Boolean
    ) = treeRoot.getBranchOrNull(excludeLeaf, predicate)

    override fun addChild(child: TreeNode<T>) = treeRoot.addChild(child)
    override fun removeChild(child: TreeNode<T>) = treeRoot.removeChild(child)

    override fun hasImmediateChild(target: TreeNode<T>) = treeRoot.hasImmediateChild(target)
    override fun hasChildRecursive(target: TreeNode<T>) = treeRoot.hasChildRecursive(target)
    override fun toTreeValue(depth: Int, parentIndex: Int, yourIndex: Int) =
        treeRoot.toTreeValue(depth, parentIndex, yourIndex)

    override fun numChildrenRecursive() = treeRoot.numChildrenRecursive()
    override fun numChildrenImmedate() = treeRoot.numChildrenImmedate()
    override fun print(depth: Int) = treeRoot.print(depth)

    fun toImmutableTree(): ImmutableTree<T> {
        val totalChildren = numChildrenRecursive()
        val root = toTreeValue(0, -1, 0)

        return ImmutableTree(root.first, totalChildren)
    }

    fun print() = treeRoot.print(0)

    override fun equals(other: Any?): Boolean {
        return if (other is Tree<*>) {
            treeRoot == other.root()
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return treeRoot.hashCode()
    }

    operator fun get(index: Int): TreeNode<T> {
        val totalChildren = treeRoot.numChildrenRecursive() + 1
        if (index >= totalChildren) {
            throw IndexOutOfBoundsException()
        } else {
            var foundNode: TreeNode<T>? = null
            for ((ii, node) in treeRoot.iterator().withIndex()) {
                if (index == ii) {
                    foundNode = node
                    break
                }
            }

            if (foundNode != null) {
                return foundNode
            } else {
                throw IndexOutOfBoundsException()
            }
        }
    }

    override fun insertChild(at: Int, child: TreeNode<T>) = treeRoot.insertChild(at, child)

    override fun indexOfChild(child: TreeNode<T>): Int = treeRoot.indexOfChild(child)
}

class NodeNotFound : IllegalStateException("Tree node not found")