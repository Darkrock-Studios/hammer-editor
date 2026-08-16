package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServerSecureRandomTest {

	private val strongInstance = Regex("""SecureRandom\.getInstanceStrong\s*\(""")

	@Test
	fun `server rng does not read dev random`() {
		assertNotEquals("NativePRNGBlocking", nonBlockingSecureRandom().algorithm)
	}

	@Test
	fun `no source reaches for getInstanceStrong`() {
		val root = File("src/main/kotlin")
		assertTrue(
			root.isDirectory,
			"Expected the test working directory to be the :server module (got ${File("").absolutePath})",
		)

		val offenders = root.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.filter { strongInstance.containsMatchIn(it.readText()) }
			.map { it.relativeTo(root).invariantSeparatorsPath }
			.sorted()
			.toList()

		assertEquals(
			emptyList(),
			offenders,
			"SecureRandom.getInstanceStrong() resolves to NativePRNGBlocking on Linux and blocks " +
				"on an entropy-starved host. Use nonBlockingSecureRandom() instead.",
		)
	}
}
