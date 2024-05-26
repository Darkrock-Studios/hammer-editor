package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectroot.Router
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import kotlinx.serialization.Serializable

interface Notes : HammerComponent, Router {
	val stack: Value<ChildStack<Config, Destination>>

	sealed class Destination {
		data class BrowseNotesDestination(val component: BrowseNotes) : Destination()

		data class ViewNoteDestination(val component: ViewNote) : Destination()

		data class CreateNoteDestination(val component: CreateNote) : Destination()
	}

	@Serializable
	sealed class Config {
		@Serializable
		data class BrowseNotesConfig(val projectDef: ProjectDef) : Config()

		@Serializable
		data class ViewNoteConfig(val noteId: Int) : Config()

		@Serializable
		data class CreateNoteConfig(val projectDef: ProjectDef) : Config()
	}

	fun showBrowse()
	fun showViewNote(noteId: Int)
	fun showCreateNote()
}