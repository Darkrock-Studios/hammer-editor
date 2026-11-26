package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheetComponent
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CommandPaletteComponent

/**
 * The palette and cheat sheet slot. It is apart from the modal slot so either opens over focus
 * mode, or any other modal, without closing it.
 */
class CommandOverlayRouter(
	componentContext: ComponentContext,
	private val actions: ActionRegistry,
) {
	private val navigation = SlotNavigation<Config>()

	// Kept for as long as the project is open, not saved.
	private var cheatSheetShowAll = true

	val state: Value<ChildSlot<Config, ProjectRoot.CommandOverlay>> =
		componentContext.childSlot(
			source = navigation,
			initialConfiguration = { Config.None },
			key = "CommandOverlayRouter",
			childFactory = ::createChild,
			// Not saved: a palette restored with the root would list what was live before the
			// root and its screens had registered anything.
			serializer = null,
		)

	private val current: Config
		get() = state.value.child?.configuration ?: Config.None

	/** False once the overlay in the slot has started to close, while its exit plays. */
	private val currentIsOpen: Boolean
		get() = when (val overlay = state.value.child?.instance) {
			is ProjectRoot.CommandOverlay.PaletteOverlay -> overlay.component.state.value.open
			is ProjectRoot.CommandOverlay.CheatSheetOverlay -> overlay.component.state.value.open
			ProjectRoot.CommandOverlay.None, null -> false
		}

	private fun createChild(config: Config, componentContext: ComponentContext): ProjectRoot.CommandOverlay =
		when (config) {
			Config.None -> ProjectRoot.CommandOverlay.None
			Config.Palette -> ProjectRoot.CommandOverlay.PaletteOverlay(
				CommandPaletteComponent(componentContext, actions, ::clear)
			)

			Config.CheatSheet -> ProjectRoot.CommandOverlay.CheatSheetOverlay(
				CheatSheetComponent(
					componentContext,
					actions,
					showAll = cheatSheetShowAll,
					onShowAllChanged = { cheatSheetShowAll = it },
					onClosed = ::clear,
				)
			)
		}

	fun showPalette() = show(Config.Palette)

	fun showCheatSheet() = show(Config.CheatSheet)

	/** Starts closing the overlay; the slot clears once its UI has played the exit. */
	fun dismiss() {
		when (val overlay = state.value.child?.instance) {
			is ProjectRoot.CommandOverlay.PaletteOverlay -> overlay.component.dismiss()
			is ProjectRoot.CommandOverlay.CheatSheetOverlay -> overlay.component.dismiss()
			ProjectRoot.CommandOverlay.None, null -> {}
		}
	}

	/** Whether [config] is up and not yet closing. */
	fun isShowing(config: Config): Boolean = current == config && currentIsOpen

	private fun show(config: Config) {
		if (isShowing(config)) return
		// The one in the slot, open or playing its exit, is destroyed first, so its barrier is down
		// before the new one looks at what is live.
		if (current != Config.None) clear()
		navigation.activate(config)
	}

	private fun clear() {
		navigation.activate(Config.None)
	}

	sealed class Config {
		data object None : Config()

		data object Palette : Config()

		data object CheatSheet : Config()
	}
}
