package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord

/** Every action that has a chord, by category, marking those that do nothing where it opened. */
interface CheatSheet {
	val state: Value<State>

	/** Starts closing the sheet; its UI plays the exit, then calls [onDismissed]. */
	fun dismiss()

	/** The exit has played; the sheet leaves. */
	fun onDismissed()

	/**
	 * [Reach.Editor] marks a chord the focused text editor handles: the registry cannot see
	 * focus, so the sheet does not claim it is live or dead.
	 */
	enum class Reach { Live, Dead, Editor }

	data class Row(val id: ActionId, val label: String, val chords: List<KeyChord>, val reach: Reach)
	data class Section(val category: String, val rows: List<Row>)

	/** [loaded] once the labels have resolved; [open] until it starts closing. */
	data class State(
		val sections: List<Section> = emptyList(),
		val loaded: Boolean = false,
		val open: Boolean = true,
	)
}
