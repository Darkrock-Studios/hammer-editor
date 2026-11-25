package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class CommandPaletteComponent(
	componentContext: ComponentContext,
	private val actions: ActionRegistry,
	onDismiss: () -> Unit,
) : ComponentBase(componentContext), CommandPalette {

	private val strRes: StrRes by inject()

	private val _state = MutableValue(CommandPalette.State())
	override val state: Value<CommandPalette.State> = _state

	// Taken before the barrier goes up, so it is what is live under whatever the palette covers.
	private val offered = actions.live().filter { it.id != HammerActions.PaletteOpen.id }

	private val barrier = CommandOverlayBarrier(actions, lifecycle, onDismiss)

	init {
		scope.launch {
			val entries = offered.map { def ->
				PaletteEntry(
					id = def.id,
					label = strRes.get(def.label),
					category = strRes.get(def.category.label),
					chord = actions.keymap.primaryChord(def.id),
				)
			}
			withContext(dispatcherMain) { _state.value = CommandPalette.State(entries) }
		}
	}

	override fun run(id: ActionId) {
		if (barrier.dismiss()) actions.invoke(id)
	}

	override fun dismiss() {
		barrier.dismiss()
	}
}
