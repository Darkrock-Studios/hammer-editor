package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry

/** Lists the actions live when it opened and runs the one picked. */
interface CommandPalette {
	val state: Value<State>

	/** Starts closing the palette, then runs [id]. */
	fun run(id: ActionId)

	/** Starts closing the palette; its UI plays the exit, then calls [onDismissed]. */
	fun dismiss()

	/** The exit has played; the palette leaves. */
	fun onDismissed()

	/** [loaded] once the labels have resolved; [open] until it starts closing. */
	data class State(
		val entries: List<PaletteEntry> = emptyList(),
		val loaded: Boolean = false,
		val open: Boolean = true,
	)
}
