import com.darkrockstudios.build.PackageFormat
import com.darkrockstudios.build.resolvePackageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackageFormatTest {

	@Test
	fun `no format property means not packaged`() {
		assertEquals(PackageFormat.NONE, resolvePackageFormat(null))
		assertEquals(PackageFormat.NONE, resolvePackageFormat(""))
		assertEquals(PackageFormat.NONE, resolvePackageFormat("  "))
	}

	@Test
	fun `every token resolves to its own format`() {
		for (format in PackageFormat.entries) {
			assertEquals(format, resolvePackageFormat(format.token))
		}
	}

	@Test
	fun `an unknown token fails the build`() {
		val error = assertFailsWith<IllegalStateException> { resolvePackageFormat("windows") }
		assertEquals(true, error.message?.contains("Unknown -Pformat=windows"))
	}

	@Test
	fun `tokens are unique`() {
		val tokens = PackageFormat.entries.map { it.token }
		assertEquals(tokens.toSet().size, tokens.size)
	}
}
