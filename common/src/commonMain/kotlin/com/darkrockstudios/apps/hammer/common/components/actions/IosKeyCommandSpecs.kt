package com.darkrockstudios.apps.hammer.common.components.actions

/** One `UIKeyCommand`: [key] with [command] and [shift] as its modifier flags. */
data class IosKeyCommandSpec(val key: ChordKey, val command: Boolean, val shift: Boolean, val actionId: String)

/** A key command for every chord of every host-dispatched action in [keymap]. */
fun iosKeyCommandSpecs(keymap: Keymap): List<IosKeyCommandSpec> =
	HammerActions.all
		.filter { it.dispatch == ActionDispatch.Host }
		.flatMap { def ->
			keymap.chordsFor(def.id).map { chord ->
				IosKeyCommandSpec(chord.key, command = chord.primary, shift = chord.shift, def.id.value)
			}
		}
