package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatch
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
		IosKeyCommandSpec("1", command = true, shift = false, actionId = "nav.home"),
		specs.single { it.actionId == "nav.home" },
	)

	@Test
	fun `function keys use the UIKit private-use characters`() {
		assertEquals("", specs.single { it.actionId == "project.sync" }.input)
		assertEquals("", specs.single { it.actionId == "help.cheatSheet" }.input)
		assertEquals(listOf("", ""), specs.filter { it.actionId.startsWith("pane.") }.map { it.input })
	}

	@Test
	fun `page keys use the UIKit input constants`() {
		assertEquals("UIKeyInputPageDown", specs.single { it.actionId == "item.next" }.input)
		assertEquals("UIKeyInputPageUp", specs.single { it.actionId == "item.prev" }.input)
	}

	@Test
	fun `letters are lowercase`() = assertEquals("n", specs.single { it.actionId == "item.new" }.input)

	@Test
	fun `enter is a carriage return`() = assertEquals(
		IosKeyCommandSpec("\r", command = true, shift = true, actionId = "story.focusMode"),
		specs.single { it.actionId == "story.focusMode" },
	)
}
