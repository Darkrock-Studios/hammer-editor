import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.desktop.needsUtf8Relaunch
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Utf8LocaleTest {
	@Test
	fun `Linux without a UTF-8 locale relaunches`() {
		assertTrue(needsUtf8Relaunch(HostOs.Linux, "ANSI_X3.4-1968", alreadyRelaunched = false))
	}

	@Test
	fun `Linux with a UTF-8 locale does not`() {
		assertFalse(needsUtf8Relaunch(HostOs.Linux, "UTF-8", alreadyRelaunched = false))
	}

	@Test
	fun `a relaunched process never relaunches again`() {
		assertFalse(needsUtf8Relaunch(HostOs.Linux, "ANSI_X3.4-1968", alreadyRelaunched = true))
	}

	@Test
	fun `other systems never relaunch`() {
		assertFalse(needsUtf8Relaunch(HostOs.Windows, "Cp1252", alreadyRelaunched = false))
		assertFalse(needsUtf8Relaunch(HostOs.MacOs, "US-ASCII", alreadyRelaunched = false))
	}
}
