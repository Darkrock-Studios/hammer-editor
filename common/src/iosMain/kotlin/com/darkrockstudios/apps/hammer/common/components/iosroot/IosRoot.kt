package com.darkrockstudios.apps.hammer.common.components.iosroot

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.arkivanov.parcelize.darwin.Parcelable
import com.arkivanov.parcelize.darwin.Parcelize
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface IosRoot {
	val slot: Value<ChildSlot<Config, Destination>>

	sealed class Config : Parcelable {
		@Parcelize
		data object ProjectSelect : Config()

		@Parcelize
		data class ProjectRoot(val projectDef: ProjectDef) : Config()
	}

	sealed class Destination {
		data class ProjectSelectDestination(val component: ProjectSelection) : Destination()
		data class ProjectRootDestination(val component: com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot) : Destination()
	}

	fun closeProject()
}