package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

data class ProjectData(
	val definition: ProjectDef,
	val metadata: ProjectMetadata
)