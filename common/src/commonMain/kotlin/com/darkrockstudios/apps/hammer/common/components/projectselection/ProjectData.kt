package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData as StoredData

@Serializable
data class ProjectData(
	val definition: ProjectDef,
	val metadata: ProjectMetadata,
	val storedData: StoredData = StoredData(),
	val totalWords: Int? = null,
)