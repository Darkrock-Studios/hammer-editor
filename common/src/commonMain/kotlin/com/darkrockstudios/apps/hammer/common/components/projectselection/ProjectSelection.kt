package com.darkrockstudios.apps.hammer.common.components.projectselection

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.project_select_nav_about_app
import com.darkrockstudios.apps.hammer.project_select_nav_about_app_short
import com.darkrockstudios.apps.hammer.project_select_nav_account_settings
import com.darkrockstudios.apps.hammer.project_select_nav_account_settings_short
import com.darkrockstudios.apps.hammer.project_select_nav_projects_list
import com.darkrockstudios.apps.hammer.project_select_nav_projects_list_short
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

interface ProjectSelection : HammerComponent, BackHandlerOwner {
	val stack: Value<ChildStack<Config, Destination>>
	val navRailState: Value<NavRailState>

	fun isAtRoot(): Boolean
	fun onBack()

	fun showLocation(location: Locations)
	fun toggleNavRailExpanded()

	data class NavRailState(val expanded: Boolean)

	enum class Locations(val text: StringResource, val shortText: StringResource) {
		Projects(
			Res.string.project_select_nav_projects_list,
			Res.string.project_select_nav_projects_list_short,
		),
		Settings(
			Res.string.project_select_nav_account_settings,
			Res.string.project_select_nav_account_settings_short,
		),
		AboutApp(
			Res.string.project_select_nav_about_app,
			Res.string.project_select_nav_about_app_short,
		),
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