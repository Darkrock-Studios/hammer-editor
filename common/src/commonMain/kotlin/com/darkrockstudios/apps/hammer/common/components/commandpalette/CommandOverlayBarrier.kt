package com.darkrockstudios.apps.hammer.common.components.commandpalette

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistrar
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistration

/**
 * The overlay barrier a command overlay holds from creation until its exit has played or it is
 * destroyed, so Esc reaches it. The component holds it rather than its dialog shell: a palette
 * must step out of the way to run an action while the shell stays mounted.
 *
 * Like a dialog shell's barrier, it stays up through the exit: an Esc then is taken but closes
 * nothing more, so it never reaches what lies below.
 */
internal class CommandOverlayBarrier(
	actions: ActionRegistrar,
	lifecycle: Lifecycle,
	private val onClose: () -> Unit,
) {
	var isOpen: Boolean = true
		private set

	private var stepAside = false

	private var registration: ActionRegistration? = actions.register(
		ActionLayer.Overlay,
		emptyMap(),
		blocksBelow = true,
		onDismiss = { close() },
		isActive = { !stepAside && lifecycle.state >= Lifecycle.State.STARTED },
	)

	init {
		lifecycle.doOnDestroy(::release)
	}

	/**
	 * Starts the close, then runs [andThen] as if the barrier were not there. False, doing
	 * nothing, once closing has started or the overlay is destroyed.
	 */
	fun close(andThen: () -> Unit = {}): Boolean {
		if (!isOpen || registration == null) return false
		isOpen = false
		onClose()
		stepAside = true
		try {
			andThen()
		} finally {
			stepAside = false
		}
		return true
	}

	/** The exit has played: takes the barrier down. False, doing nothing, while open or once down. */
	fun finishExit(): Boolean {
		if (isOpen || registration == null) return false
		release()
		return true
	}

	private fun release() {
		registration?.unregister()
		registration = null
	}
}
