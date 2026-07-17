package com.darkrockstudios.apps.hammer.common.components.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchSavedState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.sync_menu_group
import com.darkrockstudios.apps.hammer.sync_menu_item
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

private const val GLOBAL_SEARCH_STATE_KEY = "global_search_state"

class ProjectRootComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	onCloseProject: (() -> Unit),
	initialDeepLink: ProjectDeepLink? = null,
) : ProjectComponentBase(projectDef, componentContext), ProjectRoot {

	private val syncJournal: SyncJournal by projectInject()
	private val sceneEditor: SceneEditorService by projectInject()
	private val projectDataRepository: ProjectDataRepository by projectInject()
	private val encyclopediaService: EncyclopediaService by projectInject()
	private val settingsRepository: GlobalSettingsStore by inject()
	private val searchProjectUseCase: SearchProjectUseCase by projectInject()
	private val protocolMismatchRepository: ProtocolMismatchRepository by inject()

	// Retained on this long-lived parent so search state survives the modal being dismissed/reopened
	// and config changes; stateKeeper carries the query/filter slice across process death.
	private val globalSearchState: GlobalSearchState = run {
		val saved = stateKeeper.consume(GLOBAL_SEARCH_STATE_KEY, GlobalSearchSavedState.serializer())
		retainedInstance {
			GlobalSearchState(
				searchProjectUseCase = searchProjectUseCase,
				mainContext = dispatcherMain,
				initialQuery = saved?.query ?: "",
				initialFilter = saved?.filter ?: GlobalSearchFilter.All,
			)
		}
	}.also { searchState ->
		stateKeeper.register(GLOBAL_SEARCH_STATE_KEY, GlobalSearchSavedState.serializer()) {
			GlobalSearchSavedState(searchState.state.value.query, searchState.state.value.filter)
		}
	}

	private var pendingDeepLink: ProjectDeepLink? = initialDeepLink

	private val _projectTheme = MutableValue(ProjectRoot.ProjectThemeState(theme = null))
	override val projectTheme: Value<ProjectRoot.ProjectThemeState> = _projectTheme

	private val _navRailState = MutableValue(
		ProjectRoot.NavRailState(expanded = settingsRepository.globalSettings.navRailExpanded)
	)
	override val navRailState: Value<ProjectRoot.NavRailState> = _navRailState

	private val _backEnabled = MutableValue(true)
	override val backEnabled = _backEnabled

	override fun onBack() {
		router.onBack()
	}

	private val _closeRequestHandlers = MutableValue<Set<CloseConfirm>>(emptySet())
	override val closeRequestHandlers = _closeRequestHandlers

	private val router = ProjectRootRouter(
		componentContext,
		projectDef,
		addMenu,
		removeMenu,
		::updateCloseConfirmRequirement,
		::showProjectSync,
		::showGlobalSearch,
		::showGlobalSearchForTag,
		::showFocusMode,
		::showEncyclopediaEntry,
		::showEditorScene,
		onCloseProject,
		scope,
		dispatcherMain,
		settingsRepository.globalSettings.initialProjectScreen,
	)

	private val modalRouter = ProjectRootModalRouter(
		componentContext,
		projectDef,
		globalSearchState,
		::navigateGlobalSearchResult,
		::reopenSceneAfterFocusMode,
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

		scope.launch {
			projectDataRepository.load()
			projectDataRepository.state.collect { stored ->
				withContext(dispatcherMain) {
					_projectTheme.value = ProjectRoot.ProjectThemeState(theme = stored?.data?.theme)
				}
			}
		}

		scope.launch {
			settingsRepository.globalSettingsUpdates.collect { settings ->
				if (_navRailState.value.expanded != settings.navRailExpanded) {
					withContext(dispatcherMain) {
						_navRailState.update { it.copy(expanded = settings.navRailExpanded) }
					}
				}
			}
		}

		pendingDeepLink?.let { link ->
			pendingDeepLink = null
			navigateToDeepLink(link)
		}

		scope.launch {
			protocolMismatchRepository.mismatches.collect { info ->
				withContext(dispatcherMain) {
					modalRouter.showProtocolMismatch(info)
				}
			}
		}
	}

	override fun toggleNavRailExpanded() {
		scope.launch {
			settingsRepository.updateSettings { it.copy(navRailExpanded = !it.navRailExpanded) }
		}
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

	private fun showEncyclopediaEntry(entryDef: EntryDef) {
		showEncyclopedia()
		(routerState.value.active.instance as? ProjectRoot.Destination.EncyclopediaDestination)
			?.component?.showViewEntry(entryDef)
	}

	private fun showEditorScene(sceneItem: SceneItem) {
		showEditor()
		(routerState.value.active.instance as? ProjectRoot.Destination.EditorDestination)
			?.component?.showScene(sceneItem)
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

	override fun showGlobalSearch() = modalRouter.showGlobalSearch()

	override fun showGlobalSearchForTag(tag: String) =
		modalRouter.showGlobalSearch(initialQuery = "#$tag")

	override fun dismissGlobalSearch() = modalRouter.dismissGlobalSearch()

	override fun showFocusMode(sceneItem: SceneItem) {
		(routerState.value.active.instance as? ProjectRoot.Destination.EditorDestination)
			?.component?.closeDetails()
		modalRouter.showFocusMode(sceneItem)
	}

	override fun dismissFocusMode() = modalRouter.dismissFocusMode()

	private fun reopenSceneAfterFocusMode(sceneItem: SceneItem) {
		(routerState.value.active.instance as? ProjectRoot.Destination.EditorDestination)
			?.component?.showScene(sceneItem)
	}

	private fun navigateGlobalSearchResult(result: SearchResult) {
		when (result) {
			is SearchResult.Scene -> {
				showEditor()
				(routerState.value.active.instance as? ProjectRoot.Destination.EditorDestination)
					?.component?.showScene(result.sceneItem)
			}

			is SearchResult.Note -> navigateToDeepLink(ProjectDeepLink.Note(result.noteId))

			is SearchResult.EncyclopediaEntry -> showEncyclopediaEntry(result.entryDef)

			is SearchResult.TimelineEvent -> navigateToDeepLink(ProjectDeepLink.TimelineEvent(result.eventId))
		}
		dismissGlobalSearch()
	}

	override fun navigateToDeepLink(link: ProjectDeepLink) {
		when (link) {
			is ProjectDeepLink.Scene -> {
				val sceneItem = sceneEditor.getSceneItemFromId(link.sceneId)
				if (sceneItem == null) {
					Napier.w("Deep link skipped: no scene for id ${link.sceneId}")
					return
				}
				showEditorScene(sceneItem)
			}

			is ProjectDeepLink.Note -> {
				showNotes()
				(routerState.value.active.instance as? ProjectRoot.Destination.NotesDestination)
					?.component?.showViewNote(link.noteId)
			}

			is ProjectDeepLink.EncyclopediaEntry -> {
				val entryDef = encyclopediaService.findEntryDef(link.entryId)
				if (entryDef == null) {
					Napier.w("Deep link skipped: no encyclopedia entry for id ${link.entryId}")
					return
				}
				showEncyclopediaEntry(entryDef)
			}

			is ProjectDeepLink.TimelineEvent -> {
				showTimeLine()
				(routerState.value.active.instance as? ProjectRoot.Destination.TimeLineDestination)
					?.component?.showViewEvent(link.eventId)
			}
		}
	}

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

			if (syncJournal.shouldAutoSync()) {
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
		if (syncJournal.isServerSynchronized()) {
			addMenu(
				MenuDescriptor(
					id = SYNC_MENU_ID,
					label = Res.string.sync_menu_group,
					items = listOf(
						MenuItemDescriptor(
							id = "project-root-sync-start",
							label = Res.string.sync_menu_item,
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
		if (syncJournal.isServerSynchronized()) {
			removeMenu(SYNC_MENU_ID)
		}
	}

	companion object {
		const val SYNC_MENU_ID = "project-root-sync"
	}
}