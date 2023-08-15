package com.darkrockstudios.apps.hammer.common.util

import dev.icerock.moko.resources.StringResource

actual class StrResImpl : StrRes {
	actual override fun get(str: StringResource) = "not implemented"
	actual override fun get(str: StringResource, vararg args: Any) = "not implemented"
}