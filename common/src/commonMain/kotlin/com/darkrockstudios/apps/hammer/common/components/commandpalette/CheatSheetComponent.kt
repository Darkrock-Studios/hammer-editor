package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.components.actions.ActionCategory
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatch
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class CheatSheetComponent(
	componentContext: ComponentContext,
	private val actions: ActionRegistry,
	onlyAvailable: Boolean = false,
	private val onOnlyAvailableChanged: (Boolean) -> Unit = {},
	private val onClosed: () -> Unit,
) : ComponentBase(componentContext), CheatSheet {

	private val strRes: StrRes by inject()

	private val _state = MutableValue(CheatSheet.State(onlyAvailable = onlyAvailable))
	override val state: Value<CheatSheet.State> = _state

	// Taken before the barrier goes up.
	private val reachWhenOpened: Map<ActionId, CheatSheet.Reach> = HammerActions.all.associate {
		it.id to when {
			it.dispatch == ActionDispatch.Local -> CheatSheet.Reach.Editor
			actions.isLive(it.id) -> CheatSheet.Reach.Live
			else -> CheatSheet.Reach.Dead
		}
	}

	private val barrier = CommandOverlayBarrier(actions, lifecycle) { _state.update { it.copy(open = false) } }

	init {
		scope.launch {
			val sections = ActionCategory.entries.mapNotNull { category ->
				val rows = HammerActions.all
					.filter { it.category == category && actions.keymap.chordsFor(it.id).isNotEmpty() }
					.map { def ->
						CheatSheet.Row(
							id = def.id,
							label = strRes.get(def.label),
							chords = actions.keymap.chordsFor(def.id),
							reach = reachWhenOpened.getValue(def.id),
						)
					}
				if (rows.isEmpty()) null else CheatSheet.Section(strRes.get(category.label), rows)
			}
			withContext(dispatcherMain) { _state.update { it.copy(sections = sections, loaded = true) } }
		}
	}

	override fun dismiss() {
		barrier.close()
	}

	override fun setOnlyAvailable(only: Boolean) {
		_state.update { it.copy(onlyAvailable = only) }
		onOnlyAvailableChanged(only)
	}

	override fun onDismissed() {
		if (barrier.finishExit()) onClosed()
	}
}
