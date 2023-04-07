package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class BeginProjectsSyncResponse(
	val syncId: String,
	val projects: Set<String>,
	val deletedProjects: Set<String>,
)