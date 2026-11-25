package com.darkrockstudios.apps.hammer.common.components.actions

import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.subscribe

class ActionHandler(val isEnabled: () -> Boolean = { true }, val perform: () -> Unit)

fun interface ActionRegistration {
	fun unregister()
}

interface ActionRegistrar {
	/**
	 * [blocksBelow] hides every lower registration, and earlier ones in the same layer, from
	 * all but [ActionDef.global] actions. An [ActionLayer.Overlay] barrier with [onDismiss] is
	 * one Esc can close: see [ActionDispatcher.dismissTopOverlay].
	 */
	fun register(
		layer: ActionLayer,
		handlers: Map<ActionId, ActionHandler>,
		blocksBelow: Boolean = false,
		onDismiss: (() -> Unit)? = null,
	): ActionRegistration
}

interface ActionDispatcher {
	val keymap: Keymap

	/** False when nothing answers the chord, so the host passes the key on. */
	fun dispatch(chord: KeyChord): Boolean
	fun invoke(id: ActionId): Boolean
	fun isLive(id: ActionId): Boolean

	/** The actions that would run now, in [HammerActions.all] order. */
	fun live(): List<ActionDef>

	/** Whether an [ActionLayer.Overlay] barrier is up. */
	fun hasOverlay(): Boolean

	/**
	 * Runs the dismiss of the most recent overlay barrier that has one. Barriers without one
	 * (a component's guard under its own dialog) are passed over. False when none ran.
	 */
	fun dismissTopOverlay(): Boolean
}

/**
 * Layered action handlers: the highest [ActionLayer] answers first, then the most recent
 * registration within a layer. Main thread only.
 */
class ActionRegistry(override val keymap: Keymap = Keymap.Default) : ActionRegistrar, ActionDispatcher {

	private class Registration(
		val layer: ActionLayer,
		val seq: Long,
		val blocksBelow: Boolean,
		val handlers: Map<ActionId, ActionHandler>,
		val onDismiss: (() -> Unit)?,
	) {
		val isOverlay: Boolean get() = layer == ActionLayer.Overlay && blocksBelow
	}

	private val registrations = mutableListOf<Registration>()
	private var nextSeq = 0L

	override fun register(
		layer: ActionLayer,
		handlers: Map<ActionId, ActionHandler>,
		blocksBelow: Boolean,
		onDismiss: (() -> Unit)?,
	): ActionRegistration {
		val registration = Registration(layer, nextSeq++, blocksBelow, handlers, onDismiss)
		registrations += registration
		return ActionRegistration { registrations.remove(registration) }
	}

	override fun dispatch(chord: KeyChord): Boolean {
		val id = keymap.actionFor(chord) ?: return false
		if (HammerActions.byId(id)?.dispatch == ActionDispatch.Local) return false
		return invoke(id)
	}

	override fun invoke(id: ActionId): Boolean {
		val handler = resolve(id) ?: return false
		handler.perform()
		return true
	}

	override fun isLive(id: ActionId): Boolean = resolve(id) != null

	override fun live(): List<ActionDef> = HammerActions.all.filter { isLive(it.id) }

	override fun hasOverlay(): Boolean = registrations.any { it.isOverlay }

	override fun dismissTopOverlay(): Boolean {
		val dismiss = registrations
			.filter { it.isOverlay && it.onDismiss != null }
			.maxByOrNull { it.seq }
			?.onDismiss
			?: return false
		dismiss()
		return true
	}

	private fun resolve(id: ActionId): ActionHandler? {
		val def = HammerActions.byId(id) ?: return null
		val ordered = registrations.sortedWith(
			compareByDescending<Registration> { it.layer.ordinal }.thenByDescending { it.seq }
		)
		var blocked = false
		for (reg in ordered) {
			if (!blocked || def.global) {
				reg.handlers[id]?.takeIf { it.isEnabled() }?.let { return it }
			}
			if (reg.blocksBelow) blocked = true
		}
		return null
	}
}

/** Registers [handlers] each time [lifecycle] starts and drops them when it stops. */
fun ActionRegistrar.registerWhileStarted(
	lifecycle: Lifecycle,
	layer: ActionLayer,
	handlers: Map<ActionId, ActionHandler>,
	blocksBelow: Boolean = false,
) {
	var registration: ActionRegistration? = null
	lifecycle.subscribe(
		onStart = { registration = register(layer, handlers, blocksBelow) },
		onStop = {
			registration?.unregister()
			registration = null
		},
	)
}

/**
 * Holds an [ActionLayer.Overlay] barrier, answering [handlers], while [lifecycle] is started and
 * [isOpen] holds for [state].
 */
fun <T : Any> ActionRegistrar.registerOverlayWhile(
	lifecycle: Lifecycle,
	state: Value<T>,
	handlers: Map<ActionId, ActionHandler> = emptyMap(),
	isOpen: (T) -> Boolean,
) {
	var barrier: ActionRegistration? = null
	fun lift() {
		barrier?.unregister()
		barrier = null
	}
	state.subscribe(lifecycle) { value ->
		if (!isOpen(value)) {
			lift()
		} else if (barrier == null) {
			barrier = register(ActionLayer.Overlay, handlers, blocksBelow = true)
		}
	}
	lifecycle.subscribe(onStop = ::lift)
}
