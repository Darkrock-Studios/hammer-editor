package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

class EncyclopediaComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {

	private val navigation = StackNavigation<Config>()

	private val _stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = Config.BrowseEntriesConfig(projectDef = projectDef),
		key = "ProjectRootRouter",
		childFactory = ::createChild
	)

	override val stack: Value<ChildStack<Config, Encyclopedia.Destination>>
		get() = _stack

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): Encyclopedia.Destination =
		when (config) {
			is Config.BrowseEntriesConfig -> Encyclopedia.Destination.BrowseEntriesDestination(
				createBrowseEntries(config, componentContext)
			)

			is Config.ViewEntryConfig -> Encyclopedia.Destination.ViewEntryDestination(
				createViewEntry(config, componentContext)
			)

			is Config.CreateEntryConfig -> Encyclopedia.Destination.CreateEntryDestination(
				createCreateEntry(config, componentContext)
			)
		}

	override fun showBrowse() {
		navigation.popWhile { it !is Config.BrowseEntriesConfig }
	}

	override fun showViewEntry(entryDef: EntryDef) {
		navigation.push(Config.ViewEntryConfig(entryDef))
	}

	override fun showCreateEntry() {
		navigation.push(Config.CreateEntryConfig(projectDef))
	}

	override fun isAtRoot(): Boolean {
		return stack.value.active.configuration is Config.BrowseEntriesConfig
	}

	private fun createBrowseEntries(
		config: Config.BrowseEntriesConfig,
		componentContext: ComponentContext
	): BrowseEntries {
		return BrowseEntriesComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
	}

	private fun createViewEntry(config: Config.ViewEntryConfig, componentContext: ComponentContext): ViewEntry {
		return ViewEntryComponent(
			componentContext = componentContext,
			entryDef = config.entryDef,
			addMenu = addMenu,
			removeMenu = removeMenu
		)
	}

	private fun createCreateEntry(config: Config.CreateEntryConfig, componentContext: ComponentContext): CreateEntry {
		return CreateEntryComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
	}

	private val backButtonHandler = object : BackCallback() {
		override fun onBack() {
			if (!isAtRoot()) {
				navigation.pop()
			}
		}
	}

	init {
		backHandler.register(backButtonHandler)

		stack.observe(lifecycle) {
			backButtonHandler.isEnabled = !isAtRoot()
			updateShouldClose()
		}
	}

	sealed class Config : Parcelable {
		@Parcelize
		data class BrowseEntriesConfig(val projectDef: ProjectDef) : Config()

		@Parcelize
		data class ViewEntryConfig(val entryDef: EntryDef) : Config()

		@Parcelize
		data class CreateEntryConfig(val projectDef: ProjectDef) : Config()
	}
}