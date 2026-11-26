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

	private fun newCheatSheet(onlyAvailable: Boolean = false, onChanged: (Boolean) -> Unit = {}) =
		CheatSheetComponent(context, registry, onlyAvailable, onChanged) { dismissed++ }

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

		assertEquals(CheatSheet.Reach.Live, sheet.row("nav.home").reach)
		assertEquals(CheatSheet.Reach.Dead, sheet.row("nav.notes").reach)
	}

	@Test
	fun `editor chords are neither live nor dead`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		assertEquals(CheatSheet.Reach.Editor, sheet.row("edit.bold").reach)
	}

	@Test
	fun `the sheet is not loaded until its labels resolve`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet()
		val loadedAtOpen = sheet.state.value.loaded
		context.resume()
		advanceUntilIdle()

		assertFalse(loadedAtOpen)
		assertTrue(sheet.state.value.loaded)
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
		assertEquals(0, dismissed)
	}

	@Test
	fun `the sheet plays its exit, then leaves and lifts its barrier`() = runTest(mainTestDispatcher) {
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler {}))
		val sheet = newCheatSheet()
		context.resume()
		advanceUntilIdle()

		sheet.dismiss()
		val openAfterDismiss = sheet.state.value.open
		val escDuringExit = registry.dismissTopOverlay()
		val blockedDuringExit = !registry.dispatch(KeyChord.mod(ChordKey.N))
		sheet.onDismissed()

		assertFalse(openAfterDismiss)
		assertTrue(escDuringExit)
		assertTrue(blockedDuringExit)
		assertEquals(1, dismissed)
		assertFalse(registry.hasOverlay())
		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.N)))
	}

	@Test
	fun `only available hides dead rows but keeps editor ones`() = runTest(mainTestDispatcher) {
		registry.register(ActionLayer.Root, mapOf(HammerActions.NavHome.id to ActionHandler {}))
		var changedTo: Boolean? = null
		val sheet = newCheatSheet(onChanged = { changedTo = it })
		context.resume()
		advanceUntilIdle()

		sheet.setOnlyAvailable(true)
		val shown = sheet.state.value.shownSections.flatMap { it.rows }.map { it.id.value }

		assertTrue("nav.home" in shown)
		assertFalse("nav.notes" in shown)
		assertTrue("edit.bold" in shown)
		assertTrue(sheet.state.value.shownSections.all { it.rows.isNotEmpty() })
		assertEquals(true, changedTo)
	}

	@Test
	fun `a sheet opened with only available set starts filtered`() = runTest(mainTestDispatcher) {
		val sheet = newCheatSheet(onlyAvailable = true)
		context.resume()
		advanceUntilIdle()

		assertTrue(sheet.state.value.onlyAvailable)
		assertTrue(sheet.state.value.shownSections.flatMap { it.rows }.none { it.reach == CheatSheet.Reach.Dead })
	}
}
