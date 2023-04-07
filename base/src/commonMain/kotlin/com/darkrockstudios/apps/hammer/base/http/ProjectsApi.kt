package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class GetProjectsResponse(
	val projects: Set<String>,
	val deletedProjects: Set<String>,
)