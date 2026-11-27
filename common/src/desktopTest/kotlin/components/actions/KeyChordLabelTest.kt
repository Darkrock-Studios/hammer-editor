package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KeyChordLabelTest {

	@Test
	fun `Apple labels use symbols`() {
		assertEquals("⇧⌘N", KeyChord.mod(ChordKey.N, shift = true).label(ChordPlatform.DesktopMac))
		assertEquals("⌘↩", KeyChord.mod(ChordKey.Enter).label(ChordPlatform.Ios))
		assertEquals("⇧F6", KeyChord.bare(ChordKey.F6, shift = true).label(ChordPlatform.DesktopMac))
	}

	@Test
	fun `other labels spell the modifiers`() {
		assertEquals("Ctrl+Shift+N", KeyChord.mod(ChordKey.N, shift = true).label(ChordPlatform.Desktop))
		assertEquals("Shift+F6", KeyChord.bare(ChordKey.F6, shift = true).label(ChordPlatform.Android))
		assertEquals("Ctrl+PgDn", KeyChord.mod(ChordKey.PageDown).label(ChordPlatform.Desktop))
		assertEquals("F3", KeyChord.bare(ChordKey.F3).label(ChordPlatform.Desktop))
	}
}
