package com.darkrockstudios.apps.hammer.common.util

import com.darkrockstudios.apps.hammer.common.isDevelopmentMode
import dev.icerock.moko.resources.StringResource
import io.github.aakira.napier.Napier
import java.util.*

actual class StrResImpl : StrRes {
	actual override fun get(str: StringResource) = str.localized()
	actual override fun get(str: StringResource, vararg args: Any): String {
		return try {
			str.localized(args = args)
		} catch (e: MissingFormatArgumentException) {
			Napier.e("String for Key: ${str.key} was missing format arg. ${args.size} args passed", e)
			if (isDevelopmentMode) {
				throw e
			} else {
				// Something recognizable but not UI breaking
				"~~!!!~~"
			}
		}
	}
}