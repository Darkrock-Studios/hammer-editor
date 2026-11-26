package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class KeyboardShortcutsDocTest {

	private val doc = File(System.getProperty("hammer.keyboardShortcutsDoc")).readText()

	@Test
	fun `every action id is documented`() {
		val missing = HammerActions.all.map { it.id.value }.filter { "`$it`" !in doc }

		assertEquals(emptyList(), missing)
	}
}
