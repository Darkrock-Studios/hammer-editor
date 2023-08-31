package com.darkrockstudios.apps.hammer.common.parcelize

import com.arkivanov.essenty.parcelable.Parceler
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant
import platform.Foundation.NSCoder

internal actual object InstantParceler : Parceler<Instant>

internal actual object StringResourceParceler : Parceler<StringResource> {
	override fun create(coder: NSCoder): StringResource {
		val id = coder.decodeStringForKey(key = "stringResource")
		return StringResource(resourceId = id)
	}


	override fun StringResource.write(coder: NSCoder) {
		coder.encodeString(value = resourceId, forKey = "stringResource")
	}
}