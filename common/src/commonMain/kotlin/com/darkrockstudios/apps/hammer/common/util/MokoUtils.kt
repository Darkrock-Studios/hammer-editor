package com.darkrockstudios.apps.hammer.common.util

import dev.icerock.moko.resources.StringResource

expect class StrRes {
	fun get(str: StringResource): String
	fun get(str: StringResource, vararg args: Any): String
}