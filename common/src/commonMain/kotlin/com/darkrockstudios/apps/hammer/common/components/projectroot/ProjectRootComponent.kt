package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectRootComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ProjectRoot {

	private val synchronizer: ClientProjectSynchronizer by projectInject()
	private val sceneEditor: SceneEditorRepository by projectInject()

	private val _backEnabled = MutableValue(true)
	override val backEnabled = _backEnabled

	private val _closeRequestHandlers = MutableValue<Set<CloseConfirm>>(emptySet())
	override val closeRequestHandlers = _closeRequestHandlers

	private val router = ProjectRootRouter(
		componentContext,
		projectDef,
		addMenu,
		removeMenu,
		::updateCloseConfirmRequirement,
		::showProjectSync,
		scope,
		dispatcherMain
	)

	private val modalRouter = ProjectRootModalRouter(
		componentContext,
		projectDef
	)

	override val routerState: Value<ChildStack<*, ProjectRoot.Destination<*>>>
		get() = router.state

	override val modalRouterState: Value<ChildSlot<ProjectRootModalRouter.Config, ProjectRoot.ModalDestination>>
		get() = modalRouter.state

	override fun onCreate() {
		super.onCreate()

		sceneEditor.subscribeToBufferUpdates(null, scope) {
			updateCloseConfirmRequirement()
		}

		handleSyncDialogCompletion()
	}

	private fun handleSyncDialogCompletion() {
		scope.launch {
			// Listen for the sync dialog closing, if we are in the process of closing, mark it as dealt with
			modalRouterState.subscribe {
				if (it.child?.configuration == ProjectRootModalRouter.Config.None
					&& closeRequestHandlers.value.isNotEmpty()
				) {
					closeRequestDealtWith(CloseConfirm.Sync)
				}
			}
		}
	}

	override fun showEditor() {
		router.showEditor()
	}

	override fun showNotes() {
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
		return sceneEditor.hasDirtyBuffers()
	}

	override suspend fun storeDirtyBuffers() {
		sceneEditor.storeAllBuffers()
	}

	override fun isAtRoot() = router.isAtRoot() && modalRouter.isAtRoot()

	override fun showProjectSync() = modalRouter.showProjectSync()

	override fun dismissProjectSync() = modalRouter.dismissProjectSync()

	private fun updateCloseConfirmRequirement() {
		_backEnabled.value = router.isAtRoot()
	}

	override fun closeRequestDealtWith(item: CloseConfirm) {
		_closeRequestHandlers.getAndUpdate {
			it.toMutableSet().apply {
				remove(item)
			}
		}
	}

	override fun requestClose() {
		scope.launch {
			val list = mutableSetOf<CloseConfirm>()
			if (hasUnsavedBuffers()) {
				list.add(CloseConfirm.Scenes)
			}

			list.addAll(router.shouldConfirmClose())

			if (synchronizer.shouldAutoSync()) {
				list.add(CloseConfirm.Sync)
			}

			list.add(CloseConfirm.Complete)
			withContext(dispatcherMain) {
				_closeRequestHandlers.update { list }
			}
		}
	}

	override fun cancelCloseRequest() {
		_closeRequestHandlers.update { emptySet() }
	}

	override fun onStart() {
		super.onStart()
		addMenuItems()
	}

	override fun onStop() {
		super.onStop()
		removeMenuItems()
	}

	private fun addMenuItems() {
		if (synchronizer.isServerSynchronized()) {
			addMenu(
				MenuDescriptor(
					id = "project-root-sync",
					label = MR.strings.sync_menu_group,
					items = listOf(
						MenuItemDescriptor(
							id = "project-root-sync-start",
							label = MR.strings.sync_menu_item,
							icon = "",
							shortcut = KeyShortcut(keyCode = 0x72),
							action = { showProjectSync() }
						)
					)
				)
			)
		}
	}

	private fun removeMenuItems() {
		if (synchronizer.isServerSynchronized()) {
			removeMenu("project-root-sync")
		}
	}
}