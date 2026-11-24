package com.darkrockstudios.apps.hammer.common.data.nux

import kotlinx.serialization.Serializable

/** Per-install record of which new-user experiences have already been shown. */
@Serializable
data class NuxState(
	/** Ids are kept as strings so hints written by a newer build survive a round trip. */
	val seenHints: Set<String> = emptySet(),
)
