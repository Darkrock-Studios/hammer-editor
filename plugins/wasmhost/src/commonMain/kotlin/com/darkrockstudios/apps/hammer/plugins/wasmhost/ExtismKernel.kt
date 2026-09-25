package com.darkrockstudios.apps.hammer.plugins.wasmhost

/**
 * The host side of Extism's shared memory, implemented natively instead of by running Extism's
 * kernel module. Plugins see opaque u64 offsets into it and copy through the `extism:host/env`
 * load and store imports, which must stay inside one live block. Everything allocated during a
 * call is dropped by [reset] before the next.
 */
internal class ExtismKernel {
	private var memory = ByteArray(INITIAL_SIZE)
	private var top = FIRST_OFFSET

	// Blocks in allocation order, which is also address order. A freed block's length is FREED.
	private var starts = LongArray(INITIAL_BLOCKS)
	private var lengths = IntArray(INITIAL_BLOCKS)
	private var blockCount = 0

	var input = 0L
		private set
	var output = 0L
		private set
	var outputLength = 0L
		private set
	var error = 0L
		private set

	fun reset() {
		top = FIRST_OFFSET
		blockCount = 0
		input = 0
		output = 0
		outputLength = 0
		error = 0
		if (memory.size > RETAINED_SIZE) memory = ByteArray(INITIAL_SIZE)
	}

	fun alloc(size: Long): Long {
		require(size in 0..MAX_MEMORY) { "Allocation of $size bytes refused" }
		if (size == 0L) return 0
		val offset = top
		val end = offset + size
		require(end <= MAX_MEMORY) { "Plugin memory exhausted" }
		if (end > memory.size) memory = memory.copyOf(maxOf(memory.size * 2L, end).coerceAtMost(MAX_MEMORY).toInt())
		if (blockCount == starts.size) {
			starts = starts.copyOf(blockCount * 2)
			lengths = lengths.copyOf(blockCount * 2)
		}
		starts[blockCount] = offset
		lengths[blockCount] = size.toInt()
		blockCount++
		top = (end + ALIGN - 1) / ALIGN * ALIGN
		return offset
	}

	fun free(offset: Long) {
		val block = blockIndex(offset)
		if (block >= 0 && starts[block] == offset) lengths[block] = FREED
	}

	/** The length of the live block starting at [offset], or 0. */
	fun length(offset: Long): Long {
		val block = blockIndex(offset)
		return if (block >= 0 && starts[block] == offset && lengths[block] != FREED) lengths[block].toLong() else 0
	}

	fun loadU8(offset: Long): Int = memory[checked(offset, 1)].toInt() and 0xFF

	fun loadU64(offset: Long): Long {
		val at = checked(offset, 8)
		var value = 0L
		for (i in 7 downTo 0) value = (value shl 8) or (memory[at + i].toLong() and 0xFF)
		return value
	}

	fun storeU8(offset: Long, value: Int) {
		memory[checked(offset, 1)] = value.toByte()
	}

	fun storeU64(offset: Long, value: Long) {
		val at = checked(offset, 8)
		for (i in 0 until 8) memory[at + i] = (value ushr (8 * i)).toByte()
	}

	fun inputLength(): Long = length(input)

	fun inputLoadU8(index: Long): Int = loadU8(input + index)

	fun inputLoadU64(index: Long): Long = loadU64(input + index)

	/** [length] may be less than the block's, as Extism allows. */
	fun outputSet(offset: Long, length: Long) {
		require(length in 0..length(offset)) { "Output of $length bytes does not fit its block" }
		output = offset
		outputLength = length
	}

	fun errorSet(offset: Long) {
		error = offset
	}

	fun write(bytes: ByteArray): Long {
		val offset = alloc(bytes.size.toLong())
		bytes.copyInto(memory, offset.toInt())
		return offset
	}

	fun read(offset: Long, length: Long = length(offset)): ByteArray {
		if (length == 0L) return ByteArray(0)
		val at = checked(offset, length.toInt())
		return memory.copyOfRange(at, at + length.toInt())
	}

	fun setInput(bytes: ByteArray) {
		input = write(bytes)
	}

	private fun checked(offset: Long, width: Int): Int {
		val block = blockIndex(offset)
		require(block >= 0 && lengths[block] != FREED && offset + width <= starts[block] + lengths[block]) {
			"Access outside plugin memory at $offset"
		}
		return offset.toInt()
	}

	/** The last block starting at or before [offset], or -1. */
	private fun blockIndex(offset: Long): Int {
		var low = 0
		var high = blockCount - 1
		var found = -1
		while (low <= high) {
			val mid = (low + high) ushr 1
			if (starts[mid] <= offset) {
				found = mid
				low = mid + 1
			} else {
				high = mid - 1
			}
		}
		return found
	}

	private companion object {
		/** Zero is the null offset. */
		const val FIRST_OFFSET = 8L
		const val ALIGN = 8L
		const val INITIAL_SIZE = 64 * 1024
		const val INITIAL_BLOCKS = 64

		/** Memory a finished call may keep for the next; beyond this it is released. */
		const val RETAINED_SIZE = 1024 * 1024
		const val MAX_MEMORY = 256L * 1024 * 1024
		const val FREED = -1
	}
}
