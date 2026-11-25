package com.darkrockstudios.apps.hammer.common.components.actions

/** One `UIKeyCommand`: [input] is the UIKit input string, [command] and [shift] its modifier flags. */
data class IosKeyCommandSpec(val input: String, val command: Boolean, val shift: Boolean, val actionId: String)

/** A key command for every chord of every host-dispatched action in [keymap]. */
fun iosKeyCommandSpecs(keymap: Keymap): List<IosKeyCommandSpec> =
	HammerActions.all
		.filter { it.dispatch == ActionDispatch.Host }
		.flatMap { def ->
			keymap.chordsFor(def.id).map { chord ->
				IosKeyCommandSpec(chord.key.uiKitInput(), command = chord.primary, shift = chord.shift, def.id.value)
			}
		}

private fun ChordKey.uiKitInput(): String = when (this) {
	in ChordKey.A..ChordKey.Z -> display.lowercase()
	in ChordKey.Digit0..ChordKey.Digit9 -> display
	// UIKeyInputF1 through UIKeyInputF12 are U+F704 through U+F70F.
	in ChordKey.F1..ChordKey.F12 -> (UIKIT_F1 + (ordinal - ChordKey.F1.ordinal)).toString()
	ChordKey.PageUp -> "UIKeyInputPageUp"
	ChordKey.PageDown -> "UIKeyInputPageDown"
	ChordKey.Enter -> "\r"
	ChordKey.Equals -> "="
	ChordKey.Minus -> "-"
	else -> error("No UIKit input for $this")
}

private const val UIKIT_F1 = ''
