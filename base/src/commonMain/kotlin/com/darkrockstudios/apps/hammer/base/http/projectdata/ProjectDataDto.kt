package com.darkrockstudios.apps.hammer.base.http.projectdata

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDataDto(
	val data: ProjectData,
	val hash: String,
)

/**
 * [originalHash] is null when the client has never synced this project's data before.
 * [hash] is the client-computed content hash of [data]; the server stores it verbatim alongside
 * the payload.
 */
@Serializable
data class ProjectDataUploadRequest(
	val data: ProjectData,
	val originalHash: String? = null,
	val hash: String,
)

@Serializable
data class ProjectDataConflictDto(
	val server: ProjectData,
	val serverHash: String,
)
