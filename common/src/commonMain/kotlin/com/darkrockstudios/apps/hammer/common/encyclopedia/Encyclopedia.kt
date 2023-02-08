package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.projectroot.Router

interface Encyclopedia : Router {
	val stack: Value<ChildStack<Config, Destination>>

	sealed class Destination {
		data class BrowseEntriesDestination(val component: BrowseEntries) : Destination()

		data class ViewEntryDestination(val component: ViewEntry) : Destination()

		data class CreateEntryDestination(val component: CreateEntry) : Destination()
	}

	sealed class Config : Parcelable {
		@Parcelize
		data class BrowseEntriesConfig(val projectDef: ProjectDef) : Config()

		@Parcelize
		data class ViewEntryConfig(val entryDef: EntryDef) : Config()

		@Parcelize
		data class CreateEntryConfig(val projectDef: ProjectDef) : Config()
	}

	fun showBrowse()
	fun showViewEntry(entryDef: EntryDef)
	fun showCreateEntry()
}