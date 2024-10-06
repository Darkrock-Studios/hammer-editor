package com.darkrockstudios.apps.hammer.base

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class ProjectId(val id: String) {
	companion object {
		fun fromUUID(uuid: Uuid) = ProjectId(uuid.toString())
		fun randomUUID() = ProjectId(Uuid.random().toString())
	}
}

