package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatch
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.Keymap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReservedChordsTest {

	/** composetexteditor 3.6.0 `CtrlKeyBindings` and `MacKeyBindings`; keep in sync on every texteditor upgrade. */
	private val editorChords = listOf(
		ChordKey.A, ChordKey.C, ChordKey.X, ChordKey.B, ChordKey.I, ChordKey.U,
		ChordKey.E, ChordKey.V, ChordKey.Y, ChordKey.Z,
	).map { KeyChord.mod(it) } +
		listOf(ChordKey.X, ChordKey.V, ChordKey.Z).map { KeyChord.mod(it, shift = true) } +
		listOf(
			KeyChord.bare(ChordKey.PageUp),
			KeyChord.bare(ChordKey.PageDown),
			KeyChord.bare(ChordKey.Enter),
			KeyChord.bare(ChordKey.Enter, shift = true),
		)

	private val functionKeys = (ChordKey.F1.ordinal..ChordKey.F12.ordinal).map { ChordKey.entries[it] }

	private val hostChords = HammerActions.all
		.filter { it.dispatch == ActionDispatch.Host }
		.flatMap { it.defaultChords }

	@Test
	fun `no two actions share a chord`() {
		val all = HammerActions.all.flatMap { def -> def.defaultChords.map { it to def.id } }
		assertEquals(emptyMap(), all.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 })
	}

	@Test
	fun `host chords avoid the text editor's own chords`() {
		assertEquals(emptyList(), hostChords.filter { it in editorChords })
	}

	@Test
	fun `host chords need Mod or a function key`() {
		assertEquals(emptyList(), hostChords.filter { !it.primary && it.key !in functionKeys })
	}

	@Test
	fun `the default keymap answers every table chord with its action`() {
		HammerActions.all.forEach { def ->
			def.defaultChords.forEach { assertEquals(def.id, Keymap.Default.actionFor(it)) }
		}
	}

	@Test
	fun `ids are unique`() {
		assertEquals(HammerActions.all.size, HammerActions.all.map { it.id }.toSet().size)
	}

	@Test
	fun `every action can be found by its id`() {
		HammerActions.all.forEach { assertEquals(it, HammerActions.byId(it.id)) }
	}

	@Test
	fun `palette-only actions have no chord`() {
		assertNull(Keymap.Default.primaryChord(HammerActions.ProjectGallery.id))
		assertNull(Keymap.Default.primaryChord(HammerActions.TimelineNew.id))
	}

	@Test
	fun `the primary chord is the first default`() {
		assertEquals(KeyChord.mod(ChordKey.Equals), Keymap.Default.primaryChord(HammerActions.EditTextBigger.id))
		assertEquals(
			listOf(KeyChord.mod(ChordKey.Equals), KeyChord.mod(ChordKey.Equals, shift = true)),
			Keymap.Default.chordsFor(HammerActions.EditTextBigger.id),
		)
	}
}
