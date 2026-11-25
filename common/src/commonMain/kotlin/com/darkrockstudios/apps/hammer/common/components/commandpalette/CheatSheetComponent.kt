package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
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
	onDismiss: () -> Unit,
) : ComponentBase(componentContext), CheatSheet {

	private val strRes: StrRes by inject()

	private val _state = MutableValue(CheatSheet.State())
	override val state: Value<CheatSheet.State> = _state

	// Taken before the barrier goes up. Local actions belong to the focused editor, which the
	// registry never sees, so they count as live.
	private val liveWhenOpened: Map<ActionId, Boolean> = HammerActions.all.associate {
		it.id to (it.dispatch == ActionDispatch.Local || actions.isLive(it.id))
	}

	private val barrier = CommandOverlayBarrier(actions, lifecycle, onDismiss)

	init {
		scope.launch {
			val sections = ActionCategory.entries.mapNotNull { category ->
				val rows = HammerActions.all
					.filter { it.category == category && actions.keymap.chordsFor(it.id).isNotEmpty() }
					.map { def ->
						CheatSheet.Row(def.id, strRes.get(def.label), actions.keymap.chordsFor(def.id), liveWhenOpened.getValue(def.id))
					}
				if (rows.isEmpty()) null else CheatSheet.Section(strRes.get(category.label), rows)
			}
			withContext(dispatcherMain) { _state.value = CheatSheet.State(sections) }
		}
	}

	override fun dismiss() {
		barrier.dismiss()
	}
}
