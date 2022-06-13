package com.darkrockstudios.apps.hammer.common.fileio

/**
 * Hammer Path
 * A common filesystem path object to decouple us from the specific file i/o lib
 */
data class HPath(
    val path: String,
    val isAbsolute: Boolean
)