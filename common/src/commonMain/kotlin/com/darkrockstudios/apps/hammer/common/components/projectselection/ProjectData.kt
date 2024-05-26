package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

@Serializable
data class ProjectData(
	val definition: ProjectDef,
	val metadata: ProjectMetadata
)