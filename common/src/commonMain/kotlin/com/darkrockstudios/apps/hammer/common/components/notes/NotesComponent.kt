package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.subscribe
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistrar
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.registerWhileStarted
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class NotesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val updateShouldClose: () -> Unit,
	private val actions: ActionRegistrar,
	private val onShowGlobalSearchForTag: (String) -> Unit,
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
		navigation.pushNew(Notes.Config.ViewNoteConfig(noteId))
	}

	override fun showCreateNote() {
		navigation.pushNew(Notes.Config.CreateNoteConfig(projectDef))
	}

	override fun isAtRoot() =
		stack.value.active.configuration is Notes.Config.BrowseNotesConfig

	override fun onBack() {
		navigation.pop()
	}

	override fun shouldConfirmClose(): Set<CloseConfirm> {
		val unsaved = stack.value.items.any { isUnsaved(it.instance) }
		return if (unsaved) setOf(CloseConfirm.Notes) else emptySet()
	}

	private fun isUnsaved(destination: Notes.Destination) = when (destination) {
		is Notes.Destination.CreateNoteDestination -> true
		is Notes.Destination.ViewNoteDestination -> destination.component.isEditingAndDirty()
		else -> false
	}

	override fun discardUnsaved(item: CloseConfirm) {
		if (item != CloseConfirm.Notes) return
		stack.value.items.forEach { child ->
			val view = child.instance as? Notes.Destination.ViewNoteDestination ?: return@forEach
			if (view.component.state.value.isEditing) view.component.discardEdit()
		}
		if (stack.value.active.instance is Notes.Destination.CreateNoteDestination) {
			navigation.pop()
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
			onViewNote = ::showViewNote,
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
			dismissView = ::showBrowse,
			updateShouldClose = updateShouldClose,
			actions = actions,
			onShowGlobalSearchForTag = onShowGlobalSearchForTag,
		)
	}

	private fun createCreateNote(
		config: Notes.Config.CreateNoteConfig,
		componentContext: ComponentContext
	): CreateNote {
		return CreateNoteComponent(
			componentContext = componentContext,
			projectDef = config.projectDef,
			dismissCreate = ::showBrowse,
			updateShouldClose = updateShouldClose,
		)
	}

	private val browse: BrowseNotes?
		get() = stack.value.items.firstNotNullOfOrNull {
			(it.instance as? Notes.Destination.BrowseNotesDestination)?.component
		}

	/**
	 * Steps the open note by [offset] in the list's order. Not live over a dirty edit: replacing
	 * the view would drop its draft.
	 */
	private fun noteStep(offset: Int): ActionHandler {
		fun target(): Int? {
			val view = stack.value.active.instance as? Notes.Destination.ViewNoteDestination ?: return null
			if (view.component.isEditingAndDirty()) return null
			val open = (stack.value.active.configuration as Notes.Config.ViewNoteConfig).noteId
			val order = browse?.visibleOrder ?: return null
			val index = order.indexOf(open)
			if (index < 0) return null
			return order.getOrNull(index + offset)
		}
		return ActionHandler(isEnabled = { target() != null }) {
			target()?.let { id ->
				val config = Notes.Config.ViewNoteConfig(id)
				navigation.navigate { configs -> configs.dropLast(1).filter { it != config } + config }
			}
		}
	}

	init {
		actions.registerWhileStarted(
			lifecycle,
			ActionLayer.Module,
			mapOf(
				HammerActions.ItemNew.id to ActionHandler(
					isEnabled = { stack.value.active.configuration !is Notes.Config.CreateNoteConfig },
				) { showCreateNote() },
				HammerActions.ItemNext.id to noteStep(1),
				HammerActions.ItemPrev.id to noteStep(-1),
			),
		)
		stack = componentContext.childStack(
			source = navigation,
			initialConfiguration = Notes.Config.BrowseNotesConfig(projectDef = projectDef),
			key = "NotesRouter",
			childFactory = ::createChild,
			serializer = Notes.Config.serializer(),
		)
		stack.subscribe(lifecycle) {
			updateShouldClose()
		}
	}
}