package com.darkrockstudios.apps.hammer.common.fileio

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize

/**
 * Hammer Path
 * A common filesystem path object to decouple us from the specific file i/o lib
 */
@Parcelize
data class HPath(
    val path: String,
    val name: String,
    val isAbsolute: Boolean
) : Parcelable