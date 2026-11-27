package com.darkrockstudios.apps.hammer.common.data.references

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ReferenceIndex(
	val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
	val isDirty: Boolean = false,
	val lastCalculated: Instant? = null,
	val entryToScenes: Map<Int, Set<Int>> = emptyMap(),
	/** The project this was calculated from. The cache file is found by project name, which another project can come to hold. */
	val projectId: String? = null,
) {
	companion object {
		const val CURRENT_SCHEMA_VERSION = 1
	}
}
