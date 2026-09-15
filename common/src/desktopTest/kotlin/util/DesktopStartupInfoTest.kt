package util

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.desktopStartupInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStartupInfoTest {

	private val properties = mapOf(
		"os.name" to "SomeOS",
		"os.version" to "1.0",
		"os.arch" to "amd64",
		"java.vendor" to "Vendor",
		"java.runtime.version" to "21",
	)
	private val xdgEnvironment = mapOf(
		"XDG_SESSION_TYPE" to "wayland",
		"XDG_CURRENT_DESKTOP" to "GNOME",
	)
	private val noEnvironment = emptyMap<String, String>()

	@Test
	fun `linux reports its display session`() {
		val info = desktopStartupInfo(HostOs.Linux, properties::get, xdgEnvironment::get)

		assertTrue(info.contains(" | session: wayland/GNOME | "))
	}

	@Test
	fun `linux without XDG variables still shows the session slot`() {
		val info = desktopStartupInfo(HostOs.Linux, properties::get, noEnvironment::get)

		assertTrue(info.contains(" | session: n/a/n/a | "))
	}

	@Test
	fun `windows and macOS leave the session out`() {
		for (os in listOf(HostOs.Windows, HostOs.MacOs)) {
			val info = desktopStartupInfo(os, properties::get, xdgEnvironment::get)

			assertFalse(info.contains("session"), "$os banner: $info")
		}
	}

	@Test
	fun `the rest of the banner is unchanged off linux`() {
		val info = desktopStartupInfo(HostOs.Windows, properties::get, noEnvironment::get)

		assertEquals("OS: SomeOS 1.0 (amd64) | JVM: Vendor 21 | renderApi: default", info)
	}
}
