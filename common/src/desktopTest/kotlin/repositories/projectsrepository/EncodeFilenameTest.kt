package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodeFilenameTest {

	@Test
	fun `roundtrip preserves natively-allowed characters unchanged`() {
		listOf(
			"plain name",
			"It's-a-me",
			"Chapter 3 (Part II)",
			"Hello, World!",
			"What about & this?",
			"It’s curly “quoted”",
		).forEach { input ->
			val encoded = ProjectsRepository.encodeForFilename(input)
			assertEquals(input, ProjectsRepository.decodeFromFilename(encoded), "roundtrip: $input")
		}
	}

	@Test
	fun `encode replaces OS-forbidden chars with lookalikes`() {
		val input = "A:B?C/D\\E*F\"G|H<I>J"
		val encoded = ProjectsRepository.encodeForFilename(input)
		// None of the OS-forbidden ASCII chars should remain.
		listOf(':', '?', '/', '\\', '*', '"', '|', '<', '>').forEach { ch ->
			assertFalse(encoded.contains(ch), "$ch should have been encoded; got: $encoded")
		}
		assertEquals(input, ProjectsRepository.decodeFromFilename(encoded))
	}

	@Test
	fun `encode trims trailing dot and space (Windows constraint)`() {
		assertEquals("name", ProjectsRepository.encodeForFilename("name."))
		assertEquals("name", ProjectsRepository.encodeForFilename("name "))
		assertEquals("name", ProjectsRepository.encodeForFilename("name. ."))
	}

	@Test
	fun `decode is identity on names with no encoded chars`() {
		val plain = "regular_name 123"
		assertEquals(plain, ProjectsRepository.decodeFromFilename(plain))
	}

	@Test
	fun `validateFileName accepts every char in the new allowed set`() {
		// One assertion per representative char so failures point to the offender.
		listOf(
			"a", "Z", "0", " ", "_", "'", "+",
			"-", ".", ",", "!", "?", ":", "(", ")", "&", "\"",
			"/", "\\", "*", "|", "<", ">",
			"’", "“", "”",
		).forEach { ch ->
			val name = "x${ch}y"
			assertTrue(
				ProjectsRepository.validateFileName(name).isSuccess,
				"expected '$ch' (in '$name') to validate",
			)
		}
	}

	@Test
	fun `validateFileName rejects tilde delimiter`() {
		assertTrue(ProjectsRepository.validateFileName("ok~name").isFailure)
	}

	@Test
	fun `validateFileName rejects Windows reserved basenames`() {
		listOf("CON", "con", "PRN", "AUX", "NUL", "COM0", "COM1", "LPT0", "LPT9", "CON.txt").forEach {
			assertTrue(
				ProjectsRepository.validateFileName(it).isFailure,
				"expected '$it' to be rejected as reserved",
			)
		}
	}

	@Test
	fun `validateFileName allows reserved names and leading dot for wrapped names`() {
		listOf("CON", "con", "COM0", "LPT0", ".prologue").forEach {
			assertTrue(
				ProjectsRepository.validateFileName(it, usedAsRawFilename = false).isSuccess,
				"expected '$it' to validate as a scene/group name",
			)
		}
	}

	@Test
	fun `sanitizeFileName drops disallowed chars and trims`() {
		val cleaned = ProjectsRepository.sanitizeFileName("foo @ bar # baz.")
		// @ and # are not in the allowed set; trailing dot is trimmed.
		assertEquals("foo bar baz", cleaned)
	}

	@Test
	fun `toLocalSafeName leaves an already-valid name untouched`() {
		val name = "Chapter 3 (Part II)"
		assertEquals(name, ProjectsRepository.toLocalSafeName(name))
	}

	@Test
	fun `toLocalSafeName sanitizes a server name with disallowed characters`() {
		val mangled = "Alice In Wonderland (# Name clash 2026-06-07 fk6fycC #)"
		val safe = ProjectsRepository.toLocalSafeName(mangled)

		assertFalse(safe.contains('#'), "disallowed '#' should be gone; got: $safe")
		assertTrue(ProjectsRepository.validateFileName(safe).isSuccess, "result must itself be valid")
	}

	@Test
	fun `toLocalSafeName falls back to a default when nothing legal remains`() {
		assertEquals(
			ProjectsRepository.RECOVERED_PROJECT_NAME,
			ProjectsRepository.toLocalSafeName("###"),
		)
	}
}
