package com.darkrockstudios.apps.hammer.base

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class IdeaId(val id: String) {
	companion object {
		fun fromUUID(uuid: Uuid) = IdeaId(uuid.toString())
		fun randomUUID() = IdeaId(Uuid.random().toString())
	}
}
