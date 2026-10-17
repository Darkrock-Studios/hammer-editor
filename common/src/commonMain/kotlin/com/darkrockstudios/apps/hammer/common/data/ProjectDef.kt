package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDefinition(val id: ProjectId, val name: String, val path: HPath)

typealias ProjectDef = ProjectDefinition