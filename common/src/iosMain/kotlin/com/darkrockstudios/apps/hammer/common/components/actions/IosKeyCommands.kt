package com.darkrockstudios.apps.hammer.common.components.actions

import platform.UIKit.UIKeyInputF1
import platform.UIKit.UIKeyInputF10
import platform.UIKit.UIKeyInputF11
import platform.UIKit.UIKeyInputF12
import platform.UIKit.UIKeyInputF2
import platform.UIKit.UIKeyInputF3
import platform.UIKit.UIKeyInputF4
import platform.UIKit.UIKeyInputF5
import platform.UIKit.UIKeyInputF6
import platform.UIKit.UIKeyInputF7
import platform.UIKit.UIKeyInputF8
import platform.UIKit.UIKeyInputF9
import platform.UIKit.UIKeyInputPageDown
import platform.UIKit.UIKeyInputPageUp

/** An [IosKeyCommandSpec] with its key resolved to the `UIKeyCommand` input string. */
data class IosKeyCommand(val input: String, val command: Boolean, val shift: Boolean, val actionId: String)

fun iosKeyCommands(keymap: Keymap): List<IosKeyCommand> =
	iosKeyCommandSpecs(keymap).map { spec ->
		IosKeyCommand(spec.key.uiKitInput(), spec.command, spec.shift, spec.actionId)
	}

private fun ChordKey.uiKitInput(): String = when (this) {
	ChordKey.A, ChordKey.B, ChordKey.C, ChordKey.D, ChordKey.E, ChordKey.F, ChordKey.G,
	ChordKey.H, ChordKey.I, ChordKey.J, ChordKey.K, ChordKey.L, ChordKey.M, ChordKey.N,
	ChordKey.O, ChordKey.P, ChordKey.Q, ChordKey.R, ChordKey.S, ChordKey.T, ChordKey.U,
	ChordKey.V, ChordKey.W, ChordKey.X, ChordKey.Y, ChordKey.Z -> display.lowercase()

	ChordKey.Digit0, ChordKey.Digit1, ChordKey.Digit2, ChordKey.Digit3, ChordKey.Digit4,
	ChordKey.Digit5, ChordKey.Digit6, ChordKey.Digit7, ChordKey.Digit8, ChordKey.Digit9 -> display

	ChordKey.F1 -> UIKeyInputF1
	ChordKey.F2 -> UIKeyInputF2
	ChordKey.F3 -> UIKeyInputF3
	ChordKey.F4 -> UIKeyInputF4
	ChordKey.F5 -> UIKeyInputF5
	ChordKey.F6 -> UIKeyInputF6
	ChordKey.F7 -> UIKeyInputF7
	ChordKey.F8 -> UIKeyInputF8
	ChordKey.F9 -> UIKeyInputF9
	ChordKey.F10 -> UIKeyInputF10
	ChordKey.F11 -> UIKeyInputF11
	ChordKey.F12 -> UIKeyInputF12
	ChordKey.PageUp -> UIKeyInputPageUp
	ChordKey.PageDown -> UIKeyInputPageDown
	ChordKey.Enter -> "\r"
	ChordKey.Equals -> "="
	ChordKey.Minus -> "-"
}
