package com.darkrockstudios.apps.hammer.common.compose.moko

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource

@Composable
actual fun getString(id: StringResource): String =
    androidx.compose.ui.res.stringResource(id.resourceId)

@Composable
actual fun getString(id: StringResource, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id.resourceId, args)