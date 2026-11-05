package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerSecureRandomTest {

	/** Matches the qualified call and a statically imported bare one. */
	private val strongInstance = Regex("""\bgetInstanceStrong\s*\(""")

	@Test
	fun `request path rng does not read dev random`() {
		val algorithm = nonBlockingSecureRandom().algorithm

		assertFalse(
			algorithm == "NativePRNGBlocking",
			"$algorithm reads /dev/random; a request path must never draw from it",
		)

		// Where the /dev/urandom-only variant exists, taking anything else means the
		// platform default won, and on Linux that default can be DRBG, which blocks.
		if (nonBlockingAlgorithmAvailable()) {
			assertEquals(
				NON_BLOCKING_ALGORITHM,
				algorithm,
				"This host offers $NON_BLOCKING_ALGORITHM, so it must be what gets picked",
			)
		}
	}

	private fun nonBlockingAlgorithmAvailable(): Boolean =
		runCatching { java.security.SecureRandom.getInstance(NON_BLOCKING_ALGORITHM) }.isSuccess

	@Test
	fun `getInstanceStrong stays confined to the key minting helper`() {
		val roots = listOf(File("src/main/kotlin"), File("src/testFixtures/kotlin"))
		assertTrue(
			roots.any { it.isDirectory },
			"Expected the test working directory to be the :server module (got ${File("").absolutePath})",
		)

		val offenders = roots
			.filter { it.isDirectory }
			.flatMap { root ->
				root.walkTopDown()
					.filter { it.isFile && it.extension == "kt" }
					.filter { strongInstance.containsMatchIn(it.readText()) }
					.map { it.relativeTo(root).invariantSeparatorsPath }
			}
			// The one deliberate use, documented and reachable only from CLI subcommands.
			.filterNot { it.endsWith("utilities/ServerSecureRandom.kt") }
			.sorted()

		assertEquals(
			emptyList(),
			offenders,
			"getInstanceStrong() resolves to NativePRNGBlocking on Linux and blocks on an " +
				"entropy-starved host. Use nonBlockingSecureRandom(), or keyMintingSecureRandom() " +
				"if this genuinely mints long-lived key material off any request path.",
		)
	}
}
