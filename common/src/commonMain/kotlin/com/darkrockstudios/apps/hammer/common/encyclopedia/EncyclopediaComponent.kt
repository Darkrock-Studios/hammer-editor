package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

class EncyclopediaComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {

	private val navigation = StackNavigation<Config>()

	private val _stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = Config.BrowseEntriesConfig(projectDef = projectDef),
		key = "ProjectRootRouter",
		childFactory = ::createChild
	)

	override val state: Value<ChildStack<Config, Encyclopedia.Destination>>
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
		navigation.navigate(
			transformer = { stack ->
				val newStack = stack.dropLastWhile { it !is Config.BrowseEntriesConfig }
				return@navigate newStack.ifEmpty {
					listOf(Config.BrowseEntriesConfig(projectDef))
				}
			},
			onComplete = { _, _ -> }
		)
	}

	override fun showViewEntry(entryDef: EntryDef) {
		navigation.push(Config.ViewEntryConfig(entryDef))
	}

	override fun showCreateEntry() {
		navigation.push(Config.CreateEntryConfig(projectDef))
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
			entryDef = config.entryDef
		)
	}

	private fun createCreateEntry(config: Config.CreateEntryConfig, componentContext: ComponentContext): CreateEntry {
		return CreateEntryComponent(
			componentContext = componentContext,
			projectDef = config.projectDef
		)
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