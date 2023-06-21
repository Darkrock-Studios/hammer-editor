package com.darkrockstudios.apps.hammer.common.util

import dev.icerock.moko.resources.StringResource

actual class StrRes {
	actual fun get(str: StringResource) = str.localized()
	actual fun get(str: StringResource, vararg args: Any) = str.localized(args = args)
}