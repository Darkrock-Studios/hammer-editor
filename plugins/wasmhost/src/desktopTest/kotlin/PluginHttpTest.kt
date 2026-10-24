import com.darkrockstudios.apps.hammer.plugins.wasmhost.httpHost
import com.darkrockstudios.apps.hammer.plugins.wasmhost.refusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginHttpTest {
	@Test
	fun `a URL's host is lower case without its port, path, or query`() {
		assertEquals("api.example.com", httpHost("https://API.Example.com/v2/check"))
		assertEquals("localhost", httpHost("http://localhost:8081"))
		assertEquals("example.com", httpHost("https://example.com?x=1"))
		assertEquals("[::1]", httpHost("http://[::1]:8081/v2"))
	}

	@Test
	fun `other schemes and user info have no host`() {
		assertNull(httpHost("ftp://example.com"))
		assertNull(httpHost("file:///etc/passwd"))
		assertNull(httpHost("https://allowed.com@evil.com/"))
		assertNull(httpHost("https:///path"))
	}

	@Test
	fun `declared hosts need https, and the entered host may use http`() {
		val declared = listOf("api.example.com")
		assertNull(refusal("https://api.example.com/v2/check", declared, entered = null))
		assertNotNull(refusal("http://api.example.com/v2/check", declared, entered = null))
		assertNull(refusal("http://192.168.1.20:8081/v2/check", declared, entered = "192.168.1.20"))
		assertNull(refusal("http://api.example.com/v2/check", declared, entered = "api.example.com"))
		assertNotNull(refusal("https://other.example.com/", declared, entered = "192.168.1.20"))
		assertNotNull(refusal("ftp://api.example.com/", declared, entered = null))
	}
}
