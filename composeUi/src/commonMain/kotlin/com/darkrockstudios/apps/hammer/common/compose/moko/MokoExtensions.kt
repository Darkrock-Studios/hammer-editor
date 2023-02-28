package com.darkrockstudios.apps.hammer.common.compose.moko

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource

@Composable
expect fun getString(id: StringResource): String

@Composable
expect fun getString(id: StringResource, vararg args: Any): String

@Composable
expect fun StringResource.get(): String

@Composable
expect fun StringResource.get(vararg args: Any): String