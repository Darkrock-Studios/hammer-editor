package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.Serializable

interface ProjectSelection : HammerComponent {
	val slot: Value<ChildSlot<Config, Destination>>

	fun showLocation(location: Locations)

	enum class Locations(val text: StringResource) {
		Projects(MR.strings.project_select_nav_projects_list),
		Settings(MR.strings.project_select_nav_account_settings),
		AboutApp(MR.strings.project_select_nav_about_app),
	}

	@Serializable
	sealed class Config(val location: Locations) {
		@Serializable
		data object ProjectsList : Config(Locations.Projects)

		@Serializable
		data object AccountSettings : Config(Locations.Settings)

		@Serializable
		data object AboutApp : Config(Locations.AboutApp)
	}

	sealed class Destination {
		data class ProjectsListDestination(val component: ProjectsList) : Destination()
		data class AccountSettingsDestination(val component: AccountSettings) : Destination()
		data class AboutAppDestination(val component: AboutApp) : Destination()
	}
}