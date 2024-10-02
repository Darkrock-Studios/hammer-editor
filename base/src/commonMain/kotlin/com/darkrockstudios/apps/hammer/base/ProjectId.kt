package com.darkrockstudios.apps.hammer.base

import korlibs.io.util.UUID
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProjectId(val id: String) {
	companion object {
		fun randomUUID() = ProjectId(UUID.randomUUID().toString())
	}
}

