package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface Encyclopedia : HammerComponent {
	val state: Value<ChildStack<EncyclopediaComponent.Config, Destination>>

	sealed class Destination {
		data class BrowseEntriesDestination(val component: BrowseEntries) : Destination()

		data class ViewEntryDestination(val component: ViewEntry) : Destination()

		data class CreateEntryDestination(val component: CreateEntry) : Destination()
	}

	fun showBrowse()
	fun showViewEntry(entryDef: EntryDef)
	fun showCreateEntry()
}