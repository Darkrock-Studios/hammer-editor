package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import kotlinx.serialization.Serializable

interface Encyclopedia : Router {
	val stack: Value<ChildStack<Config, Destination>>

	sealed class Destination {
		data class BrowseEntriesDestination(val component: BrowseEntries) : Destination()

		data class ViewEntryDestination(val component: ViewEntry) : Destination()

		data class CreateEntryDestination(val component: CreateEntry) : Destination()
	}

	@Serializable
	sealed class Config {
		@Serializable
		data class BrowseEntriesConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class ViewEntryConfig(val entryDef: EntryDef) : Config()

		@Serializable
		data class CreateEntryConfig(val projectDef: ProjectDef) : Config()
	}

	fun showBrowse()
	fun showViewEntry(entryDef: EntryDef)
	fun showCreateEntry()
}