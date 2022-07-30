package com.darkrockstudios.apps.hammer.common.tree

/**
 * A simplified immutable representation of a tree node
 */
data class TreeValue<T>(
    val value: T,
    val children: Set<TreeValue<T>>,
    val depth: Int,
    val totalChildren: Int
) : Iterable<TreeValue<T>> {
    override fun iterator(): Iterator<TreeValue<T>> = NodeIterator(this)

    private class NodeIterator<T>(node: TreeValue<T>) : Iterator<TreeValue<T>> {

        private var internalIterator: Iterator<TreeValue<T>> = node.children.iterator()
        private var currentChild: TreeValue<T>? = null
        private var currentChildIterator: Iterator<TreeValue<T>>? = null

        override fun hasNext(): Boolean {
            return internalIterator.hasNext() || currentChildIterator?.hasNext() == true
        }

        private fun advanceChild(): TreeValue<T> {
            val child: TreeValue<T> = internalIterator.next()
            currentChildIterator = child.iterator()
            currentChild = child
            return child
        }

        override fun next(): TreeValue<T> {
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
        str += "- " + value.toString()
        println(str)

        children.forEach { it.print(depth + 1) }
    }
}

data class ImmutableTree<T>(
    val root: TreeValue<T>,
    val totalChildren: Int
) : Iterable<TreeValue<T>> {
    operator fun get(index: Int): TreeValue<T> {
        if (index >= root.totalChildren) {
            throw IndexOutOfBoundsException()
        } else {
            var foundNode: TreeValue<T>? = null
            for ((ii, node) in root.iterator().withIndex()) {
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

    override fun iterator(): Iterator<TreeValue<T>> = root.iterator()

    fun print() {
        root.print(0)
    }
}