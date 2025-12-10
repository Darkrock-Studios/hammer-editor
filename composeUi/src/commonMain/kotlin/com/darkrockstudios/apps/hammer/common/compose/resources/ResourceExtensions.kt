package com.darkrockstudios.apps.hammer.common.compose.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun StringResource.get(): String = stringResource(this)

@Composable
fun StringResource.get(vararg args: Any): String = stringResource(this, *args)
