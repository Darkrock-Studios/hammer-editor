package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistrar
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistration

/**
 * The overlay barrier a command overlay holds from creation until it closes or is destroyed, so
 * Esc reaches it. The component holds it rather than its dialog shell: a palette must drop it
 * before running an action, while the shell stays mounted.
 */
internal class CommandOverlayBarrier(
	actions: ActionRegistrar,
	lifecycle: Lifecycle,
	private val onDismiss: () -> Unit,
) {
	private var registration: ActionRegistration? = actions.register(
		ActionLayer.Overlay,
		emptyMap(),
		blocksBelow = true,
		onDismiss = { dismiss() },
		isActive = { lifecycle.state >= Lifecycle.State.STARTED },
	)

	init {
		lifecycle.doOnDestroy(::lift)
	}

	/** Lifts the barrier and closes the overlay; false, doing nothing, once it has closed. */
	fun dismiss(): Boolean {
		if (registration == null) return false
		lift()
		onDismiss()
		return true
	}

	private fun lift() {
		registration?.unregister()
		registration = null
	}
}
