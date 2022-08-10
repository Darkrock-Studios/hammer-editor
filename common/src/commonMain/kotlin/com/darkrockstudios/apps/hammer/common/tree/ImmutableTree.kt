package com.darkrockstudios.apps.hammer.common.tree

/**
 * A simplified immutable representation of a tree node
 */
data class TreeValue<T>(
    val value: T,
    val index: Int,
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
        return if (other is TreeNode<*>?) {
            other?.value == value
        } else {
            false
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

    fun print() {
        root.print(0)
    }
}