package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDefinition(val name: String, val path: HPath)

typealias ProjectDef = ProjectDefinition

@Serializable
data class SyncedProjectDefinition(val projectDef: ProjectDefinition, val projectId: ProjectId)