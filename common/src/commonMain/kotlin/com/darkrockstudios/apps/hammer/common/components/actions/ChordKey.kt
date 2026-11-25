package com.darkrockstudios.apps.hammer.common.components.actions

/** The keys a [KeyChord] can name. Anything else is never an action key. */
enum class ChordKey(val display: String) {
	A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"), H("H"), I("I"), J("J"), K("K"), L("L"), M("M"),
	N("N"), O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"), U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z"),
	Digit0("0"), Digit1("1"), Digit2("2"), Digit3("3"), Digit4("4"),
	Digit5("5"), Digit6("6"), Digit7("7"), Digit8("8"), Digit9("9"),
	F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
	F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12"),
	PageUp("PgUp"), PageDown("PgDn"), Enter("Enter"), Equals("="), Minus("-"),
}

/**
 * A key with its modifiers. [primary] is Mod: Cmd on macOS and iOS, Ctrl elsewhere. There is
 * no Alt: AltGr arrives as Ctrl+Alt (issue #859), so an Alt chord would fire while typing.
 */
data class KeyChord(val key: ChordKey, val primary: Boolean = false, val shift: Boolean = false) {

	fun label(platform: ChordPlatform = currentChordPlatform): String =
		if (platform.modIsCmd) {
			buildString {
				if (primary) append('⌘')
				if (shift) append('⇧')
				append(if (key == ChordKey.Enter) "↩" else key.display)
			}
		} else {
			listOfNotNull(
				"Ctrl".takeIf { primary },
				"Shift".takeIf { shift },
				key.display,
			).joinToString("+")
		}

	companion object {
		fun mod(key: ChordKey, shift: Boolean = false) = KeyChord(key, primary = true, shift = shift)
		fun bare(key: ChordKey, shift: Boolean = false) = KeyChord(key, primary = false, shift = shift)
	}
}
