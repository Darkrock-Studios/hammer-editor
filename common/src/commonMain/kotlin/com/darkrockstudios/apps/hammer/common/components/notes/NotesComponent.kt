package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popWhile
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class NotesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), Notes {

	private val navigation = StackNavigation<Notes.Config>()
	override val stack: Value<ChildStack<Notes.Config, Notes.Destination>>

	private fun createChild(
		config: Notes.Config,
		componentContext: ComponentContext
	): Notes.Destination =
		when (config) {
			is Notes.Config.BrowseNotesConfig -> Notes.Destination.BrowseNotesDestination(
				createBrowseNotes(config, componentContext)
			)

			is Notes.Config.ViewNoteConfig -> Notes.Destination.ViewNoteDestination(
				createViewNote(config, componentContext)
			)

			is Notes.Config.CreateNoteConfig -> Notes.Destination.CreateNoteDestination(
				createCreateNote(config, componentContext)
			)
		}

	override fun showBrowse() {
		navigation.popWhile { it !is Notes.Config.BrowseNotesConfig }
	}

	override fun showViewNote(noteId: Int) {
		navigation.push(Notes.Config.ViewNoteConfig(noteId))
	}

	override fun showCreateNote() {
		navigation.push(Notes.Config.CreateNoteConfig(projectDef))
	}

	override fun isAtRoot() =
		stack.value.active.configuration is Notes.Config.BrowseNotesConfig

	override fun shouldConfirmClose(): Set<CloseConfirm> {
		val unsaved = when (val destination = stack.value.active.instance) {
			is Notes.Destination.CreateNoteDestination -> true
			is Notes.Destination.ViewNoteDestination -> {
				destination.component.isEditingAndDirty()
			}

			else -> false
		}

		return if (unsaved) {
			setOf(CloseConfirm.Notes)
		} else {
			emptySet()
		}
	}

	private fun createBrowseNotes(
		config: Notes.Config.BrowseNotesConfig,
		componentContext: ComponentContext
	): BrowseNotes {
		return BrowseNotesComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			onShowCreate = ::showCreateNote,
			onViewNote = ::showViewNote
		)
	}

	private fun createViewNote(
		config: Notes.Config.ViewNoteConfig,
		componentContext: ComponentContext
	): ViewNote {
		return ViewNoteComponent(
			componentContext = componentContext,
			projectDef = projectDef,
			noteId = config.noteId,
			dismissView = ::showBrowse
		)
	}

	private fun createCreateNote(
		config: Notes.Config.CreateNoteConfig,
		componentContext: ComponentContext
	): CreateNote {
		return CreateNoteComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			dismissCreate = ::showBrowse
		)
	}

	private fun closeNote() {
		navigation.pop()
	}

	private val backButtonHandler = BackCallback {
		if (!isAtRoot()) {
			navigation.pop()
		}
	}

	init {
		backHandler.register(backButtonHandler)
		stack = componentContext.childStack(
			source = navigation,
			initialConfiguration = Notes.Config.BrowseNotesConfig(projectDef = projectDef),
			key = "NotesRouter",
			childFactory = ::createChild
		)
		stack.observe(lifecycle) {
			backButtonHandler.isEnabled = !isAtRoot()
			updateShouldClose()
		}
	}
}