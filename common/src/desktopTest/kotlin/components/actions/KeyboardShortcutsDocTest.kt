package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

private const val DOC_PATH = "docs/KEYBOARD-SHORTCUTS.md"

class KeyboardShortcutsDocTest {

	private val doc = locateDoc().readText()

	@Test
	fun `every action id is documented`() {
		val missing = HammerActions.all.map { it.id.value }.filter { "`$it`" !in doc }

		assertEquals(emptyList(), missing)
	}
}

/** The Gradle task names the doc; other runners, such as an IDE's, start somewhere inside the repo. */
private fun locateDoc(): File {
	System.getProperty("hammer.keyboardShortcutsDoc")?.let { return File(it) }
	val start = File(System.getProperty("user.dir")).absoluteFile
	return generateSequence(start) { it.parentFile }
		.map { File(it, DOC_PATH) }
		.firstOrNull { it.isFile }
		?: error("$DOC_PATH not found: set hammer.keyboardShortcutsDoc or run from inside the repository ($start)")
}
