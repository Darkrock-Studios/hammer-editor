package com.darkrockstudios.apps.hammer.common.tree

/**
 * A simplified immutable representation of a tree node
 */
data class TreeValue<T>(
    val value: T,
    /** Global tree index */
    val index: Int,
    /** Parent's global tree index */
    val parent: Int,
    val children: List<TreeValue<T>>,
    val depth: Int,
    val totalChildren: Int
) : Iterable<TreeValue<T>> {
    override fun iterator(): Iterator<TreeValue<T>> = NodeIterator(this)

    private class NodeIterator<T>(private val node: TreeValue<T>) : Iterator<TreeValue<T>> {

        private var hasServedSelf: Boolean = false

        private var myChildrenIterator: Iterator<TreeValue<T>> = node.children.iterator()
        private var currentChildIterator: Iterator<TreeValue<T>>? = null

        override fun hasNext(): Boolean {
            return !hasServedSelf || myChildrenIterator.hasNext() || currentChildIterator?.hasNext() == true
        }

        private fun advanceChildAndGetFirst(): TreeValue<T> {
            val child: TreeValue<T> = myChildrenIterator.next()
            val newIterator = child.iterator()
            currentChildIterator = newIterator
            return newIterator.next()
        }

        override fun next(): TreeValue<T> {
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

    override fun hashCode(): Int {
        return children.fold(value.hashCode()) { acc, child -> (43 * acc) + child.hashCode() }
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other is TreeValue<*> -> {
                other.parent == parent &&
                        other.index == index &&
                        other.depth == depth &&
                        other.value == value &&
                        other.children == children
            }
            else -> false
        }
    }

    fun print(depth: Int) {
        var str = ""
        repeat(depth) {
            str += " "
        }
        str += "- [${index}]" + value.toString()
        println(str)

        children.forEach { it.print(depth + 1) }
    }
}

data class ImmutableTree<T>(
    val root: TreeValue<T>,
    val totalChildren: Int
) : Iterable<TreeValue<T>> {
    private val nodeIndex: HashMap<Int, TreeValue<T>>

    init {
        val newIndex = HashMap<Int, TreeValue<T>>()
        for (treeValue in root) {
            newIndex[treeValue.index] = treeValue
        }
        nodeIndex = newIndex
    }

    val totalNodes: Int
        // +1 for the root it's self
        get() = totalChildren + 1

    operator fun get(index: Int): TreeValue<T> =
        nodeIndex[index] ?: throw IndexOutOfBoundsException()

    override fun iterator(): Iterator<TreeValue<T>> = root.iterator()

    fun indexOf(node: TreeValue<T>): Int {
        var index: Int = -1
        for ((ii, curNode) in iterator().withIndex()) {
            if (node == curNode) {
                index = ii
                break
            }
        }
        return index
    }

    fun indexOf(predicate: (T) -> Boolean): Int {
        var index: Int = -1
        for ((ii, node) in iterator().withIndex()) {
            if (predicate(node.value)) {
                index = ii
                break
            }
        }
        return index
    }

    fun findBy(predicate: (T) -> Boolean): TreeValue<T>? {
        var item: TreeValue<T>? = null
        for (node in iterator()) {
            if (predicate(node.value)) {
                item = node
                break
            }
        }
        return item
    }

    fun isAncestorOf(needleIndex: Int, leafIndex: Int): Boolean {
        val leaf = get(leafIndex)

        var parentIndex = leaf.parent
        var isAncestor = false
        while (parentIndex > -1 && !isAncestor) {
            if (parentIndex == needleIndex) {
                isAncestor = true
            } else {
                val myParent = get(parentIndex)
                parentIndex = myParent.parent
            }
        }

        return isAncestor
    }

    fun getBranch(leafIndex: Int, excludeLeaf: Boolean): List<TreeValue<T>> {
        val branch = mutableListOf<TreeValue<T>>()

        val leaf = this[leafIndex]
        if (!excludeLeaf) {
            branch.add(leaf)
        }

        var curParentIndex = leaf.parent
        while (curParentIndex > -1) {
            val curParent = this[curParentIndex]
            branch.add(curParent)
            curParentIndex = curParent.parent
        }

        branch.reverse()

        return branch
    }

    fun getCoordinatesFor(node: TreeValue<T>): NodeCoordinates {
        return if (node == root) {
            NodeCoordinates.Root
        } else {
            val parent = this[node.parent]
            val childIndex = parent.children.indexOf(node)

            NodeCoordinates(
                globalIndex = node.index,
                parentIndex = parent.index,
                childLocalIndex = childIndex
            )
        }
    }

    fun getByCoordinates(coords: NodeCoordinates): TreeValue<T> {
        return if (coords.isTreeRoot()) {
            root
        } else {
            val parent = this[coords.parentIndex]
            parent.children[coords.childLocalIndex]
        }
    }

    fun print() {
        root.print(0)
    }

    private var cachedHash: Int? = null
    override fun hashCode(): Int {
        val hash = cachedHash
        return if (hash == null) {
            val newHash = root.hashCode() + totalChildren
            cachedHash = newHash
            newHash
        } else {
            hash
        }
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other is ImmutableTree<*> -> {
                if (other.totalChildren != totalChildren) {
                    false
                } else {
                    root == other.root
                }
            }
            else -> false
        }
    }
}

data class NodeCoordinates(val globalIndex: Int, val parentIndex: Int, val childLocalIndex: Int) {
    fun isTreeRoot() = (parentIndex == -1) && (childLocalIndex == -1)

    companion object {
        val Root = NodeCoordinates(0, -1, -1)
    }
}