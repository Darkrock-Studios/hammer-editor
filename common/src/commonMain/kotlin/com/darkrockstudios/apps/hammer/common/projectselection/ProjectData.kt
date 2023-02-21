package com.darkrockstudios.apps.hammer.common.projectselection

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata

data class ProjectData(
	val definition: ProjectDef,
	val metadata: ProjectMetadata
)