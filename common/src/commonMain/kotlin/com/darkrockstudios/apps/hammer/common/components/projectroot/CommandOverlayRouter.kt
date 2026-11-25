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

	private fun createChild(config: Config, componentContext: ComponentContext): ProjectRoot.CommandOverlay =
		when (config) {
			Config.None -> ProjectRoot.CommandOverlay.None
			Config.Palette -> ProjectRoot.CommandOverlay.PaletteOverlay(
				CommandPaletteComponent(componentContext, actions, ::dismiss)
			)

			Config.CheatSheet -> ProjectRoot.CommandOverlay.CheatSheetOverlay(
				CheatSheetComponent(componentContext, actions, ::dismiss)
			)
		}

	fun showPalette() = show(Config.Palette)

	fun showCheatSheet() = show(Config.CheatSheet)

	fun dismiss() {
		navigation.activate(Config.None)
	}

	fun isShowing(config: Config): Boolean = current == config

	private fun show(config: Config) {
		if (current == config) return
		// The one showing goes first, so its barrier is down before the new one looks at what is live.
		if (current != Config.None) dismiss()
		navigation.activate(config)
	}

	sealed class Config {
			data object None : Config()

			data object Palette : Config()

			data object CheatSheet : Config()
	}
}
