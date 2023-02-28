package com.darkrockstudios.apps.hammer.common.compose.moko

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource

@Composable
actual fun getString(id: StringResource): String = id.localized()

@Composable
actual fun getString(id: StringResource, vararg args: Any): String {
    return id.localized(args = args)
}