package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.projectInject
import io.github.aakira.napier.Napier

class ProjectRootComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ProjectRoot {
	private val projectEditor: ProjectEditorRepository by projectInject()
	private val notes: NotesRepository by projectInject()

	init {
		projectEditor.initializeProjectEditor()
	}

	private val _backEnabled = MutableValue(true)
	override val backEnabled = _backEnabled

	private val _shouldConfirmClose = MutableValue(false)
	override val shouldConfirmClose = _shouldConfirmClose

	private val router = ProjectRootRouter(
		componentContext,
		projectDef,
		addMenu,
		removeMenu,
		::updateCloseConfirmRequirement,
		scope,
		dispatcherMain
	)

	override val routerState: Value<ChildStack<*, ProjectRoot.Destination<*>>>
		get() = router.state

	override fun showEditor() {
		router.showEditor()
	}

	override fun showNotes() {
		Napier.d("showNotes component")
		router.showNotes()
	}

	override fun showEncyclopedia() {
		router.showEncyclopedia()
	}

	override fun showHome() {
		router.showHome()
	}

	override fun showTimeLine() {
		router.showTimeLine()
	}

	override fun showDestination(type: ProjectRoot.DestinationTypes) {
		when (type) {
			ProjectRoot.DestinationTypes.Editor -> showEditor()
			ProjectRoot.DestinationTypes.Notes -> showNotes()
			ProjectRoot.DestinationTypes.Encyclopedia -> showEncyclopedia()
			ProjectRoot.DestinationTypes.TimeLine -> showTimeLine()
			ProjectRoot.DestinationTypes.Home -> showHome()
		}
	}

	override fun hasUnsavedBuffers(): Boolean {
		return projectEditor.hasDirtyBuffers()
	}

	override fun storeDirtyBuffers() {
		projectEditor.storeAllBuffers()
	}

	override fun isAtRoot() = router.isAtRoot()

	private fun updateCloseConfirmRequirement() {
		_shouldConfirmClose.value = hasUnsavedBuffers() && router.isAtRoot()
		_backEnabled.value = router.isAtRoot()
	}

	override fun onDestroy() {
		super.onDestroy()
		Napier.i { "ProjectRootComponent closing Project Editor" }
		projectEditor.close()
		notes.close()
		projectScope.closeScope()
	}

	init {
		projectEditor.subscribeToBufferUpdates(null, scope) {
			Napier.d { "subscribeToBufferUpdates" }
			updateCloseConfirmRequirement()
		}
	}
}