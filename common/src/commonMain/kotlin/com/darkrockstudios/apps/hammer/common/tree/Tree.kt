package com.darkrockstudios.apps.hammer.common.tree

interface TreeData<T> : Iterable<T> {
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
    fun removeChild(child: TreeNode<T>): Boolean
    fun hasImmediateChild(target: TreeNode<T>): Boolean
    fun hasChildRecursive(target: TreeNode<T>): Boolean
    fun numChildrenRecursive(): Int

    fun toTreeValue(depth: Int): TreeValue<T>
    fun toImmutableTree(): ImmutableTree<T>
    fun numChildrenImmedate(): Int
    fun print(depth: Int)
}

data class TreeNode<T>(
    val value: T,
    private var parent: TreeNode<T>? = null,
    private val children: MutableSet<TreeNode<T>> = mutableSetOf()
) : TreeData<T> {

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

    override fun iterator(): Iterator<T> = NodeIterator(this)

    private class NodeIterator<T>(node: TreeNode<T>) : Iterator<T> {

        private var internalIterator: MutableIterator<TreeNode<T>> = node.children.iterator()
        private var currentChild: TreeNode<T>? = null
        private var currentChildIterator: Iterator<T>? = null

        override fun hasNext(): Boolean {
            return internalIterator.hasNext() || currentChildIterator?.hasNext() == true
        }

        private fun advanceChild(): T {
            val child = internalIterator.next()
            currentChildIterator = child.iterator()
            currentChild = child
            return child.value
        }

        override fun next(): T {
            val childIterator = currentChildIterator

            // Boot strep
            return if (currentChild == null && internalIterator.hasNext()) {
                return advanceChild()
            }
            // Normal case
            else if (childIterator != null) {
                if (childIterator.hasNext()) {
                    childIterator.next()
                } else {
                    advanceChild()
                }
            } else {
                throw IllegalStateException("No children left")
            }
        }
    }

    override fun toTreeValue(depth: Int): TreeValue<T> {
        val numChildren = numChildrenRecursive()
        val childValues = children.map { it.toTreeValue(depth + 1) }.toSet()
        return TreeValue(value, childValues, depth, numChildren)
    }

    override fun toImmutableTree(): ImmutableTree<T> {
        val root = toTreeValue(0)
        return ImmutableTree(root, root.totalChildren)
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
        str += "- " + value.toString()
        println(str)

        children.forEach { it.print(depth + 1) }
    }
}

class Tree<T> : TreeData<T> {
    private lateinit var treeRoot: TreeNode<T>

    fun setRoot(node: TreeNode<T>) {
        treeRoot = node
    }

    fun root() = treeRoot

    fun move(target: TreeNode<T>, from: TreeNode<T>, to: TreeNode<T>): Boolean {
        return if (from.hasImmediateChild(target)) {
            from.removeChild(target)
            to.addChild(target)
            true
        } else {
            false
        }
    }

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
    override fun toTreeValue(depth: Int) = treeRoot.toTreeValue(depth)
    override fun toImmutableTree() = treeRoot.toImmutableTree()
    override fun numChildrenRecursive() = treeRoot.numChildrenRecursive()
    override fun numChildrenImmedate() = treeRoot.numChildrenImmedate()
    override fun print(depth: Int) = treeRoot.print(depth)

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
}

class NodeNotFound : IllegalStateException("Tree node not found")