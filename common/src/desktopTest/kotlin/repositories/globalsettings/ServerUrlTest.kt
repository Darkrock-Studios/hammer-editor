package repositories.globalsettings

import com.darkrockstudios.apps.hammer.common.data.globalsettings.isInsecureServerUrl
import com.darkrockstudios.apps.hammer.common.data.globalsettings.parseServerUrl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerUrlTest {

	@Test
	fun `an http url asks for plaintext`() {
		val parsed = parseServerUrl("http://192.168.1.50:8080")

		assertEquals("192.168.1.50:8080", parsed.host)
		assertFalse(parsed.ssl)
	}

	@Test
	fun `an uppercase http scheme still asks for plaintext`() {
		assertFalse(parseServerUrl("HTTP://Home-Server.lan:8080").ssl)
	}

	@Test
	fun `an https url stays encrypted`() {
		val parsed = parseServerUrl("https://hammer.ink")

		assertEquals("hammer.ink", parsed.host)
		assertTrue(parsed.ssl)
	}

	@Test
	fun `a bare host stays encrypted`() {
		val parsed = parseServerUrl("hammer.ink")

		assertEquals("hammer.ink", parsed.host)
		assertTrue(parsed.ssl)
	}

	@Test
	fun `whitespace, case and a trailing slash are cleaned off the host`() {
		val parsed = parseServerUrl("  HTTP://Home-Server.lan:8080/  ")

		assertEquals("home-server.lan:8080", parsed.host)
		assertFalse(parsed.ssl)
	}

	@Test
	fun `an empty url is not treated as insecure`() {
		assertFalse(isInsecureServerUrl("   "))
	}

	@Test
	fun `isInsecureServerUrl agrees with the parsed scheme`() {
		assertTrue(isInsecureServerUrl("http://192.168.1.50:8080"))
		assertFalse(isInsecureServerUrl("https://hammer.ink"))
		assertFalse(isInsecureServerUrl("hammer.ink"))
	}
}
