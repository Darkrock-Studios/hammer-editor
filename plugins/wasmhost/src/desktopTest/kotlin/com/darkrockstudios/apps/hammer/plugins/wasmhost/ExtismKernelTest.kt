package com.darkrockstudios.apps.hammer.plugins.wasmhost

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ExtismKernelTest {

	private val kernel = ExtismKernel()

	@Test
	fun `output may be a prefix of its block`() {
		val block = kernel.write("hello world".encodeToByteArray())

		kernel.outputSet(block, 5)

		assertContentEquals("hello".encodeToByteArray(), kernel.read(kernel.output, kernel.outputLength))
		assertThrows<IllegalArgumentException> { kernel.outputSet(block, 12) }
	}

	@Test
	fun `loads and stores stay inside one live block`() {
		val first = kernel.alloc(3)
		val second = kernel.alloc(8)

		kernel.storeU8(first + 2, 7)
		assertEquals(7, kernel.loadU8(first + 2))
		assertThrows<IllegalArgumentException> { kernel.storeU8(first + 3, 1) }
		assertThrows<IllegalArgumentException> { kernel.loadU64(first) }

		kernel.storeU64(second, 0x0102030405060708)
		assertEquals(0x0102030405060708, kernel.loadU64(second))

		kernel.free(second)
		assertEquals(0, kernel.length(second))
		assertThrows<IllegalArgumentException> { kernel.loadU8(second) }
	}

	@Test
	fun `length is only for the start of a block`() {
		val block = kernel.alloc(16)

		assertEquals(16, kernel.length(block))
		assertEquals(0, kernel.length(block + 1))
		assertEquals(0, kernel.length(0))
	}

	@Test
	fun `a reset drops every block`() {
		val block = kernel.write(ByteArray(4 * 1024 * 1024))

		kernel.reset()

		assertEquals(0, kernel.length(block))
		assertThrows<IllegalArgumentException> { kernel.loadU8(block) }
	}
}
