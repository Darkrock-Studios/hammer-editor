package com.darkrockstudios.apps.hammer.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-identical to the client's typed `ProjectDataUploadRequest`/`ProjectDataDto`/
 * `ProjectDataConflictDto`, but with the data slot held as raw [JsonElement] so the server never
 * depends on the client's `ProjectData` shape. See "Server storage is shape-agnostic" in
 * docs/SYNCING-PROTOCOL.md.
 */
@Serializable
data class RawProjectDataUploadRequest(
	val data: JsonElement,
	val originalHash: String? = null,
	val hash: String? = null,
)

@Serializable
data class RawProjectDataDto(
	val data: JsonElement,
	val hash: String,
)

@Serializable
data class RawProjectDataConflictDto(
	val server: JsonElement,
	val serverHash: String,
)
