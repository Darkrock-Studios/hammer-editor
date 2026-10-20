package com.darkrockstudios.apps.hammer.base

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class GroupId(val id: String) {
	companion object {
		fun fromUUID(uuid: Uuid) = GroupId(uuid.toString())
		fun randomUUID() = GroupId(Uuid.random().toString())
	}
}
