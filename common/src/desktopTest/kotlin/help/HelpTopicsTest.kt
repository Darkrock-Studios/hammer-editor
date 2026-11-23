package help

import com.darkrockstudios.apps.hammer.common.help.HelpScreen
import com.darkrockstudios.apps.hammer.common.help.HelpSection
import com.darkrockstudios.apps.hammer.common.help.topics
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpTopicsTest {

	@Test
	fun `No help section is empty`() {
		assertTrue(HelpSection.entries.all { it.topics.isNotEmpty() })
	}

	@Test
	fun `Every help screen has at least one topic and no duplicates`() {
		HelpScreen.entries.forEach { screen ->
			assertTrue(screen.topics.isNotEmpty())
			assertEquals(screen.topics.size, screen.topics.toSet().size)
		}
	}
}
