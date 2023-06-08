package com.darkrockstudios.apps.hammer.common.util

import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.format

actual class StrRes {
	actual fun get(str: StringResource) = str.localized()
	actual fun get(str: StringResource, vararg args: Any) = str.format(args).localized()
}