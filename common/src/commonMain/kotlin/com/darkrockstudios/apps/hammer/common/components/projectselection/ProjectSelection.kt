package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectSelection : HammerComponent {
	val slot: Value<ChildSlot<Config, Destination>>

	val showProjectDirectory: Boolean

	fun showLocation(location: Locations)

	enum class Locations(val text: String) {
		Projects("Projects"),
		Sittings("Settings")
	}

	sealed class Config(val location: Locations) : Parcelable {
		@Parcelize
		object ProjectsList : Config(Locations.Projects)

		@Parcelize
		object AccountSettings : Config(Locations.Sittings)
	}

	sealed class Destination {
		data class ProjectsListDestination(val component: ProjectsList) : Destination()
		data class AccountSettingsDestination(val component: AccountSettings) : Destination()
	}
}