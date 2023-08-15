package com.darkrockstudios.apps.hammer.common.util

import dev.icerock.moko.resources.StringResource

/**
 * Helper class to resolve StringResources for each platform
 * outside of Composable functions.
 */
interface StrRes {
	fun get(str: StringResource): String
	fun get(str: StringResource, vararg args: Any): String
}

expect class StrResImpl : StrRes {
	override fun get(str: StringResource): String
	override fun get(str: StringResource, vararg args: Any): String
}