package com.darkrockstudios.apps.hammer.common.components.actions

/** Which chords run which action. Rebinding is a different [chords] map; the defaults come from [HammerActions]. */
class Keymap(private val chords: Map<ActionId, List<KeyChord>>) {

	private val actions: Map<KeyChord, ActionId> = buildMap {
		chords.forEach { (id, list) -> list.forEach { chord -> getOrPut(chord) { id } } }
	}

	fun chordsFor(id: ActionId): List<KeyChord> = chords[id].orEmpty()

	fun primaryChord(id: ActionId): KeyChord? = chordsFor(id).firstOrNull()

	fun actionFor(chord: KeyChord): ActionId? = actions[chord]

	companion object {
		val Default: Keymap by lazy { Keymap(HammerActions.all.associate { it.id to it.defaultChords }) }
	}
}
