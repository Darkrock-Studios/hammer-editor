package com.darkrockstudios.apps.hammer.common.data.groupmetadata

import com.darkrockstudios.apps.hammer.base.GroupId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class GroupMetadata(
	val info: GroupInfo
) {
	companion object {
		const val FILENAME = "group.toml"
	}
}

@Serializable
data class GroupInfo(
	/** Null only after a hand edit. Read it through `GroupMetadataDatasource.loadGroupId`, which mints one. */
	val groupId: GroupId? = null,
	val created: Instant,
	val description: String? = null,
	/** Local display state: never synced. */
	val collapsed: Boolean = false,
)
