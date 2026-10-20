package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import kotlinx.serialization.Serializable

@Serializable
data class GroupData(
	val definition: GroupDef,
	val collapsed: Boolean = false,
)

/** What a "Move to…" picker is moving. */
@Serializable
sealed class MoveTarget {
	@Serializable
	data class Project(val projectDef: ProjectDef) : MoveTarget()

	@Serializable
	data class Group(val groupDef: GroupDef) : MoveTarget()
}
