package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition

data class ProjectGroup(
	val rowId: Long,
	val uuid: GroupId,
	val name: String,
	val description: String?,
	val parentRowId: Long?,
	val parentUuid: GroupId?,
) {
	fun toApi() = ApiGroupDefinition(
		uuid = uuid,
		name = name,
		parentId = parentUuid,
		description = description,
	)
}
