package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatch
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.IosKeyCommandSpec
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import com.darkrockstudios.apps.hammer.common.components.actions.iosKeyCommandSpecs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosKeyCommandSpecsTest {

	private val specs = iosKeyCommandSpecs(Keymap.Default)

	@Test
	fun `every host chord becomes one key command`() = assertEquals(
		HammerActions.all.filter { it.dispatch == ActionDispatch.Host }.sumOf { it.defaultChords.size },
		specs.size,
	)

	@Test
	fun `local actions get no key command`() = assertTrue(specs.none { it.actionId == "edit.save" })

	@Test
	fun `Mod maps to command`() = assertEquals(
		IosKeyCommandSpec(ChordKey.Digit1, command = true, shift = false, actionId = "nav.home"),
		specs.single { it.actionId == "nav.home" },
	)

	@Test
	fun `bare function keys carry no modifiers`() {
		assertEquals(
			IosKeyCommandSpec(ChordKey.F3, command = false, shift = false, actionId = "project.sync"),
			specs.single { it.actionId == "project.sync" },
		)
		assertEquals(
			listOf(
				IosKeyCommandSpec(ChordKey.F6, command = false, shift = false, actionId = "pane.next"),
				IosKeyCommandSpec(ChordKey.F6, command = false, shift = true, actionId = "pane.prev"),
			),
			specs.filter { it.actionId.startsWith("pane.") },
		)
	}

	@Test
	fun `page keys keep the command flag`() {
		assertEquals(
			IosKeyCommandSpec(ChordKey.PageDown, command = true, shift = false, actionId = "item.next"),
			specs.single { it.actionId == "item.next" },
		)
		assertEquals(
			IosKeyCommandSpec(ChordKey.PageUp, command = true, shift = false, actionId = "item.prev"),
			specs.single { it.actionId == "item.prev" },
		)
	}

	@Test
	fun `shift and command combine`() = assertEquals(
		IosKeyCommandSpec(ChordKey.Enter, command = true, shift = true, actionId = "story.focusMode"),
		specs.single { it.actionId == "story.focusMode" },
	)
}
