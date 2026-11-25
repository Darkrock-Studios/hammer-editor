package components.commandpalette

import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheet
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheetComponent
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.KeyStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheatSheetComponentTest : ComponentTest() {

	private lateinit var registry: ActionRegistry
	private var dismissed = 0

	@BeforeEach
	override fun setup() {
		super.setup()
		setupComponentKoin(module { single<StrRes> { KeyStrRes() } })
		registry = ActionRegistry()
		dismissed = 0
	}

	private fun newCheatSheet() = CheatSheetComponent(context, registry) { dismissed++ }

	private fun CheatSheet.row(id: String): CheatSheet.Row =
		state.value.sections.flatMap { it.rows }.single { it.id.value == id }

	@Test
	fun `sections follow categories in table order`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		assertEquals(
			ActionCategory.entries.map { "<${it.label.key}>" },
			sheet.state.value.sections.map { it.category },
		)
		assertEquals(
			listOf("nav.home", "nav.story", "nav.notes", "nav.encyclopedia", "nav.timeline", "pane.next", "pane.prev"),
			sheet.state.value.sections.first().rows.map { it.id.value },
		)
	}

	@Test
	fun `rows carry their labels and chords`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		val row = sheet.row("edit.textBigger")

		assertEquals("<action_edit_text_bigger>", row.label)
		assertEquals(listOf(KeyChord.mod(ChordKey.Equals), KeyChord.mod(ChordKey.Equals, shift = true)), row.chords)
	}

	@Test
	fun `dead actions are marked`() = runTest(mainTestDispatcher) {
		registry.register(ActionLayer.Root, mapOf(HammerActions.NavHome.id to ActionHandler {}))

		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		assertTrue(sheet.row("nav.home").live)
		assertFalse(sheet.row("nav.notes").live)
	}

	@Test
	fun `editor chords count as live`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		assertTrue(sheet.row("edit.bold").live)
	}

	@Test
	fun `actions without a chord are left out`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		val ids = sheet.state.value.sections.flatMap { it.rows }.map { it.id }

		assertFalse(HammerActions.ProjectGallery.id in ids)
		assertFalse(HammerActions.TimelineNew.id in ids)
	}

	@Test
	fun `while open, chords below are blocked and Esc closes it`() = runTest(mainTestDispatcher) {
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler {}))
		newCheatSheet()
		context.resume()
		advanceUntilIdle()

		val blocked = !registry.dispatch(KeyChord.mod(ChordKey.N))
		val closed = registry.dismissTopOverlay()

		assertTrue(blocked)
		assertTrue(closed)
		assertEquals(1, dismissed)
		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.N)))
	}
}
