package com.darkrockstudios.apps.hammer.common.util

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class StrResImpl : StrRes {
	override suspend fun get(str: StringResource): String = getString(str)
	override suspend fun get(str: StringResource, vararg args: Any): String = getString(str, *args)
}
