package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord

/** Every action that has a chord, by category, marking those that do nothing where it opened. */
interface CheatSheet {
	val state: Value<State>
	fun dismiss()

	data class Row(val id: ActionId, val label: String, val chords: List<KeyChord>, val live: Boolean)
	data class Section(val category: String, val rows: List<Row>)
	data class State(val sections: List<Section> = emptyList())
}
