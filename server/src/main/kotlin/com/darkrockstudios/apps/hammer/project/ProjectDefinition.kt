package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDefinition(
	val name: String,
	val uuid: ProjectId
) {
	fun toApi() = toApi(groupId = null)

	// Placement is kept out of this class because it is part of ProjectSyncKey.
	fun toApi(groupId: GroupId?) = ApiProjectDefinition(
		name = name,
		uuid = ProjectId(uuid.id),
		groupId = groupId,
	)

	companion object {
		fun wrap(name: String, uuid: String) = ProjectDefinition(
			name = name,
			uuid = ProjectId(uuid)
		)
	}
}