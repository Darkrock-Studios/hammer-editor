package com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectMetadata(
	val info: Info
) {
	companion object {
		const val FILENAME = "project.toml"
	}
}

@Serializable
data class Info(
	val created: Instant
)