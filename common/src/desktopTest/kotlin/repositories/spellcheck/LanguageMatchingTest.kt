package repositories.spellcheck

import com.darkrockstudios.apps.hammer.common.spellcheck.isSpellCheckAllowedForProject
import com.darkrockstudios.apps.hammer.common.spellcheck.localesMatchLeniently
import com.darkrockstudios.apps.hammer.common.util.Locale
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageMatchingTest {

	private fun locale(tag: String) = Locale.forLanguageTag(tag)

	@Test
	fun `same language without regions matches`() {
		assertTrue(localesMatchLeniently(locale("en"), locale("en")))
	}

	@Test
	fun `region-less side matches any region of the same language`() {
		assertTrue(localesMatchLeniently(locale("en"), locale("en-US")))
		assertTrue(localesMatchLeniently(locale("en-US"), locale("en")))
	}

	@Test
	fun `matching language and region match`() {
		assertTrue(localesMatchLeniently(locale("pt-BR"), locale("pt-BR")))
	}

	@Test
	fun `differing regions of the same language do not match`() {
		assertFalse(localesMatchLeniently(locale("en-US"), locale("en-GB")))
	}

	@Test
	fun `different languages do not match`() {
		assertFalse(localesMatchLeniently(locale("fr"), locale("en")))
		assertFalse(localesMatchLeniently(locale("fr-FR"), locale("en-US")))
	}

	@Test
	fun `language-less locales never match`() {
		assertFalse(localesMatchLeniently(Locale.root, Locale.root))
		assertFalse(localesMatchLeniently(Locale.root, locale("en")))
	}

	@Test
	fun `matching is case-insensitive via tag normalization`() {
		assertTrue(localesMatchLeniently(locale("EN-us"), locale("en-US")))
	}

	@Test
	fun `unset project language always allows spell check`() {
		assertTrue(isSpellCheckAllowedForProject(null, locale("en-US")))
		assertTrue(isSpellCheckAllowedForProject("", locale("en-US")))
		assertTrue(isSpellCheckAllowedForProject("  ", locale("en-US")))
	}

	@Test
	fun `matching project language allows spell check`() {
		assertTrue(isSpellCheckAllowedForProject("en", locale("en-US")))
		assertTrue(isSpellCheckAllowedForProject("en-US", locale("en-US")))
	}

	@Test
	fun `mismatched project language blocks spell check`() {
		assertFalse(isSpellCheckAllowedForProject("fr", locale("en-US")))
		assertFalse(isSpellCheckAllowedForProject("en-GB", locale("en-US")))
	}
}
