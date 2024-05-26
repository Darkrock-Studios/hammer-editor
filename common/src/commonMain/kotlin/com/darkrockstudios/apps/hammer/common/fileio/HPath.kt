package com.darkrockstudios.apps.hammer.common.fileio

import kotlinx.serialization.Serializable

/**
 * Hammer Path
 * A common filesystem path object to decouple us from the specific file i/o lib
 */
@Serializable
data class HPath(
	val path: String,
	val name: String,
	val isAbsolute: Boolean
)