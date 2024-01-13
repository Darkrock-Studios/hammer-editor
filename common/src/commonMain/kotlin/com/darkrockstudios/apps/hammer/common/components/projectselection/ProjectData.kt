package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@Parcelize
data class ProjectData(
	val definition: ProjectDef,
	val metadata: ProjectMetadata
) : Parcelable