package com.darkrockstudios.apps.hammer.common.util

import org.jetbrains.compose.resources.StringResource

/**
 * Helper class to resolve StringResources for each platform
 * outside of Composable functions.
 */
interface StrRes {
	suspend fun get(str: StringResource): String
	suspend fun get(str: StringResource, vararg args: Any): String
}
