package base.http

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * tomlkt does not route every malformed-input failure through SerializationException.
 * These pin the full set [readTomlOrNull] must absorb so a stale or hand-edited cache
 * file can never crash a caller: numeric coercion (NumberFormatException), bad booleans
 * and type-mismatch casts (IllegalArgumentException), parser errors (IllegalStateException),
 * tomlkt decode errors (SerializationException), and missing/unreadable files (IOException).
 */
class ReadTomlOrNullTest {

	@Serializable
	private data class Sample(
		val count: Int,
		val enabled: Boolean = false,
		val byChapter: Map<Int, Int> = emptyMap(),
	)

	private val toml = createTomlSerializer()
	private val fileSystem = FakeFileSystem()
	private val path = "/sample.toml".toPath()

	private fun write(content: String) {
		fileSystem.write(path) { writeUtf8(content.trimIndent()) }
	}

	@Test
	fun `valid toml decodes and does not report an error`() {
		var errored = false
		fileSystem.writeToml(path, toml, Sample(count = 3, enabled = true))

		val result = fileSystem.readTomlOrNull<Sample>(path, toml) { errored = true }

		assertNotNull(result)
		assertEquals(3, result.count)
		assertTrue(result.enabled)
		assertFalse(errored)
	}

	@Test
	fun `missing file returns null and reports the error`() {
		var reported: Throwable? = null

		val result = fileSystem.readTomlOrNull<Sample>(path, toml) { reported = it }

		assertNull(result)
		assertNotNull(reported)
	}

	@Test
	fun `non-numeric int value returns null`() {
		write("""count = "Title"""")

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error) // NumberFormatException
	}

	@Test
	fun `non-numeric map key returns null`() {
		// The production crash: a Map<Int, Int> with a non-integer key.
		write(
			"""
			count = 0

			[byChapter]
			Title = 5
			"""
		)

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error) // NumberFormatException
	}

	@Test
	fun `non-boolean value returns null`() {
		write(
			"""
			count = 0
			enabled = "maybe"
			"""
		)

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error) // IllegalArgumentException from requireNotNull
	}

	@Test
	fun `type mismatch returns null`() {
		// count is an Int field but the file holds a table.
		write(
			"""
			[count]
			nested = 1
			"""
		)

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error)
	}

	@Test
	fun `malformed date-time literal returns null`() {
		// An unquoted, invalid date-time token fails in tomlkt's parser with
		// IllegalStateException rather than SerializationException.
		write("count = 2020-99-99T25:61:61Z")

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error)
	}

	@Test
	fun `malformed syntax returns null`() {
		write("count = = 3")

		val reported = capture()

		assertNull(reported.result)
		assertNotNull(reported.error) // SerializationException (UnexpectedTokenException)
	}

	private class Captured(val result: Sample?, val error: Throwable?)

	private fun capture(): Captured {
		var error: Throwable? = null
		val result = fileSystem.readTomlOrNull<Sample>(path, toml) { error = it }
		return Captured(result, error)
	}
}
