import com.darkrockstudios.apps.hammer.plugins.wasmhost.ModuleLimiter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Malformed modules are refused with [IllegalArgumentException], which the install check reports. */
class ModuleLimiterTest {

	private val limiter = ModuleLimiter()

	@Test
	fun `a section running past the end of the module is refused`() {
		assertThrows<IllegalArgumentException> { limiter.limit(MODULE_HEADER + byteArrayOf(0x00, 0x10, 0x01)) }
	}

	@Test
	fun `a section ending before what it declares is refused`() {
		// A table section of one byte, its count, claiming one table.
		assertThrows<IllegalArgumentException> { limiter.limit(MODULE_HEADER + byteArrayOf(0x04, 0x01, 0x01)) }
	}

	@Test
	fun `a table or memory whose minimum does not fit an Int is refused`() {
		val table = MODULE_HEADER + byteArrayOf(0x04, 0x08, 0x01, 0x70, 0x00) + U32_2_POW_31
		val memory = MODULE_HEADER + byteArrayOf(0x05, 0x07, 0x01, 0x00) + U32_2_POW_31

		assertThrows<IllegalArgumentException> { limiter.limit(table) }
		assertThrows<IllegalArgumentException> { limiter.limit(memory) }
	}
}
