package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
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

	private val navigation = StackNavigation<Encyclopedia.Config>()

	private val _stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = Encyclopedia.Config.BrowseEntriesConfig(projectDef = projectDef),
		key = "ProjectRootRouter",
		childFactory = ::createChild
	)

	override val stack: Value<ChildStack<Encyclopedia.Config, Encyclopedia.Destination>>
		get() = _stack

	private fun createChild(
        config: Encyclopedia.Config,
        componentContext: ComponentContext
    ): Encyclopedia.Destination =
		when (config) {
			is Encyclopedia.Config.BrowseEntriesConfig -> Encyclopedia.Destination.BrowseEntriesDestination(
                createBrowseEntries(config, componentContext)
            )

			is Encyclopedia.Config.ViewEntryConfig -> Encyclopedia.Destination.ViewEntryDestination(
                createViewEntry(config, componentContext)
            )

			is Encyclopedia.Config.CreateEntryConfig -> Encyclopedia.Destination.CreateEntryDestination(
                createCreateEntry(config, componentContext)
            )
		}

	override fun showBrowse() {
		navigation.popWhile { it !is Encyclopedia.Config.BrowseEntriesConfig }
	}

	override fun showViewEntry(entryDef: EntryDef) {
		navigation.push(Encyclopedia.Config.ViewEntryConfig(entryDef))
	}

	override fun showCreateEntry() {
		navigation.push(Encyclopedia.Config.CreateEntryConfig(projectDef))
	}

	override fun isAtRoot(): Boolean {
		return stack.value.active.configuration is Encyclopedia.Config.BrowseEntriesConfig
	}

	private fun createBrowseEntries(
        config: Encyclopedia.Config.BrowseEntriesConfig,
        componentContext: ComponentContext
    ): BrowseEntries {
		return BrowseEntriesComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
	}

	private fun createViewEntry(
        config: Encyclopedia.Config.ViewEntryConfig,
        componentContext: ComponentContext
    ): ViewEntry {
		return ViewEntryComponent(
			componentContext = componentContext,
			entryDef = config.entryDef,
			addMenu = addMenu,
			removeMenu = removeMenu
		)
	}

	private fun createCreateEntry(
        config: Encyclopedia.Config.CreateEntryConfig,
        componentContext: ComponentContext
    ): CreateEntry {
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
}