package com.darkrockstudios.apps.hammer.base.http.projectdata

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDataDto(
	val data: ProjectData,
	val hash: String,
)

/** [originalHash] is null when the client has never synced this project's data before. */
@Serializable
data class ProjectDataUploadRequest(
	val data: ProjectData,
	val originalHash: String? = null,
)

@Serializable
data class ProjectDataConflictDto(
	val server: ProjectData,
	val serverHash: String,
)
