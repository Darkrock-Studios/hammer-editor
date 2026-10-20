package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString
import kotlin.time.Instant

@Serializable
data class SceneMetadata(
	@TomlMultilineString
	val outline: String = "",
	@TomlMultilineString
	val notes: String = "",
	val currentDraftName: String = "",
	val confirmedReferences: Set<Int> = emptySet(),
	val dismissedReferences: Set<Int> = emptySet(),
	val tags: Set<String> = emptySet(),
	val created: Instant? = null,
	val lastEdited: Instant? = null,
) {
	/** Confirming an entry un-dismisses it, dismissing un-confirms it, and clearing forgets it either way. */
	fun withReferences(
		confirm: Collection<Int> = emptySet(),
		dismiss: Collection<Int> = emptySet(),
		clear: Collection<Int> = emptySet(),
	): SceneMetadata = copy(
		confirmedReferences = confirmedReferences + confirm - dismiss.toSet() - clear.toSet(),
		dismissedReferences = dismissedReferences + dismiss - confirm.toSet() - clear.toSet(),
	)
}
