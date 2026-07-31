package components.projectselection

import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent.Companion.cleanUpUrl
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent.Companion.validateUrl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerUrlValidationTest {

	@Test
	fun `a pasted http scheme is stripped`() {
		assertEquals("192.168.1.50:8080", cleanUpUrl("http://192.168.1.50:8080"))
	}

	@Test
	fun `a pasted https scheme is stripped`() {
		assertEquals("hammer.example.com", cleanUpUrl("https://hammer.example.com"))
	}

	@Test
	fun `surrounding whitespace and a trailing slash are stripped`() {
		assertEquals("hammer.example.com", cleanUpUrl("  https://hammer.example.com/  "))
	}

	@Test
	fun `a url with no scheme is left alone`() {
		assertEquals("hammer.ink", cleanUpUrl("hammer.ink"))
	}

	@Test
	fun `a cleaned pasted url validates`() {
		assertTrue(validateUrl(cleanUpUrl("http://192.168.1.50:8080")))
	}

	@Test
	fun `a domain with a port validates`() {
		assertTrue(validateUrl("hammer.example.com:8080"))
	}

	@Test
	fun `a bare lan hostname validates`() {
		assertTrue(validateUrl("homeserver:8080"))
	}

	@Test
	fun `a hostname with a hyphen validates`() {
		assertTrue(validateUrl("home-server.lan:8080"))
	}

	@Test
	fun `a mixed case hostname validates`() {
		assertTrue(validateUrl("Hammer.Example.com"))
	}

	@Test
	fun `an ip address validates`() {
		assertTrue(validateUrl("192.168.1.50"))
	}

	@Test
	fun `a blank url does not validate`() {
		assertFalse(validateUrl("   "))
	}

	@Test
	fun `a url still carrying its scheme does not validate`() {
		assertFalse(validateUrl("http://hammer.example.com"))
	}

	@Test
	fun `a url with a path does not validate`() {
		assertFalse(validateUrl("hammer.example.com/sync"))
	}

	@Test
	fun `a non numeric port does not validate`() {
		assertFalse(validateUrl("hammer.example.com:port"))
	}

	@Test
	fun `a port beyond the valid range does not validate`() {
		assertFalse(validateUrl("hammer.example.com:99999"))
	}

	@Test
	fun `a host with a trailing hyphen does not validate`() {
		assertFalse(validateUrl("hammer-.example.com"))
	}
}
