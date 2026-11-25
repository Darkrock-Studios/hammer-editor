package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry

/** Lists the actions live when it opened and runs the one picked. */
interface CommandPalette {
	val state: Value<State>

	/** Closes the palette, then runs [id]. */
	fun run(id: ActionId)
	fun dismiss()

	data class State(val entries: List<PaletteEntry> = emptyList())
}
