package com.darkrockstudios.apps.hammer.common.util

import android.content.Context
import dev.icerock.moko.resources.StringResource

actual class StrResImpl(context: Context) : StrRes {
	private val res = context.resources

	actual override fun get(str: StringResource) = res.getString(str.resourceId)
	actual override fun get(str: StringResource, vararg args: Any) = res.getString(str.resourceId, *args)
}