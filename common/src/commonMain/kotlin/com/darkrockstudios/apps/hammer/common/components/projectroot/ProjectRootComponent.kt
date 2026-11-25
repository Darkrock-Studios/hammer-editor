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
import com.darkrockstudios.apps.hammer.common.components.ComponentToaster
import com.darkrockstudios.apps.hammer.common.components.ComponentToasterImpl
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.registerWhileStarted
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchSavedState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectDisabledPluginsUseCase
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.save_all_toast
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
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
	private val onCloseProject: (() -> Unit),
	initialDeepLink: ProjectDeepLink? = null,
) : ProjectComponentBase(projectDef, componentContext), ProjectRoot, ComponentToaster by ComponentToasterImpl() {

	private val syncJournal: SyncJournal = projectGet()
	private val sceneEditor: SceneEditorService by projectInject()
	private val projectDataRepository: ProjectDataRepository by projectInject()
	private val projectDisabledPlugins: ProjectDisabledPluginsUseCase by projectInject()
	private val encyclopediaService: EncyclopediaService by projectInject()
	private val notesRepository: NotesRepository by projectInject()
	private val timeLineRepository: TimeLineRepository by projectInject()
	private val settingsRepository: GlobalSettingsStore by inject()
	private val searchProjectUseCase: SearchProjectUseCase by projectInject()
	private val protocolMismatchRepository: ProtocolMismatchRepository by inject()
	private val duplicateIdService: DuplicateIdService by projectInject()
	private val imagePickRequests: ImagePickRequests by projectInject()
	private val projectsRepository: ProjectsRepository by inject()

	/** Sync is set up and turned on for this project on this device. */
	private val canSync: Boolean
		get() = syncJournal.isServerSynchronized() && projectSyncEnabled
	// Set by the first syncEnabledUpdates emission, which is read off the main thread.
	private var projectSyncEnabled = false

	override val actions = ActionRegistry()

	// Retained on this long-lived parent so search state survives the modal being dismissed/reopened
	// and config changes; stateKeeper carries the query/filter slice across process death.
	private val globalSearchState: GlobalSearchState = run {
		val saved = stateKeeper.consume(GLOBAL_SEARCH_STATE_KEY, GlobalSearchSavedState.serializer())
		retainedInstance(key = GLOBAL_SEARCH_STATE_KEY) {
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

	private val _disabledPlugins = MutableValue(emptySet<String>())
	override val disabledPlugins: Value<Set<String>> = _disabledPlugins

	private val _navRailState = MutableValue(
		ProjectRoot.NavRailState(expanded = settingsRepository.globalSettings.navRailExpanded)
	)
	override val navRailState: Value<ProjectRoot.NavRailState> = _navRailState

	private val _backEnabled = MutableValue(true)
	override val backEnabled = _backEnabled

	private val _unsavedBuffers = MutableValue(false)
	override val unsavedBuffers: Value<Boolean> = _unsavedBuffers

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
		::startProjectSync,
		::showGlobalSearch,
		::showGlobalSearchForTag,
		::showImageGallery,
		::showFocusMode,
		::showEncyclopediaEntry,
		::showEditorScene,
		::showTimelineSettings,
		::showTimelineEvent,
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

	init {
		actions.registerWhileStarted(
			lifecycle,
			ActionLayer.Root,
			mapOf(
				HammerActions.NavHome.id to ActionHandler { showHome() },
				HammerActions.NavStory.id to ActionHandler { showEditor() },
				HammerActions.NavNotes.id to ActionHandler { showNotes() },
				HammerActions.NavEncyclopedia.id to ActionHandler { showEncyclopedia() },
				HammerActions.NavTimeline.id to ActionHandler { showTimeLine() },
				HammerActions.SearchGlobal.id to ActionHandler { showGlobalSearch() },
				HammerActions.ProjectSync.id to ActionHandler(isEnabled = { canSync }) { startProjectSync() },
				HammerActions.ProjectSaveAll.id to ActionHandler { saveAllBuffers() },
				HammerActions.ProjectGallery.id to ActionHandler { showImageGallery() },
				HammerActions.ProjectClose.id to ActionHandler { onCloseProject() },
			),
		)
	}

	private fun saveAllBuffers() {
		scope.launch {
			storeDirtyBuffers()
			showToast(Res.string.save_all_toast)
		}
	}

	override fun onCreate() {
		super.onCreate()

		_unsavedBuffers.value = sceneEditor.hasDirtyBuffers()
		sceneEditor.subscribeToBufferUpdates(null, scope) {
			_unsavedBuffers.value = sceneEditor.hasDirtyBuffers()
			updateCloseConfirmRequirement()
		}

		handleSyncDialogCompletion()

		scope.launch {
			projectsRepository.syncEnabledUpdates(projectDef).collect { enabled ->
				withContext(dispatcherMain) { projectSyncEnabled = enabled }
			}
		}

		scope.launch {
			projectDataRepository.load()
			projectDataRepository.state.collect { stored ->
				withContext(dispatcherMain) {
					_projectTheme.value = ProjectRoot.ProjectThemeState(theme = stored?.data?.theme)
				}
			}
		}

		scope.launch {
			projectDisabledPlugins.disabledPlugins.collect { disabled ->
				withContext(dispatcherMain) { _disabledPlugins.value = disabled }
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

		if (duplicateIdService.unresolved.value.isNotEmpty()) {
			modalRouter.showDuplicateIds()
		}

		pendingDeepLink?.let { link ->
			pendingDeepLink = null
			scope.launch { navigateToDeepLink(link) }
		}

		scope.launch {
			protocolMismatchRepository.mismatches.collect { info ->
				withContext(dispatcherMain) {
					modalRouter.showProtocolMismatch(info)
				}
			}
		}

		scope.launch {
			imagePickRequests.requests.collect { target ->
				withContext(dispatcherMain) {
					modalRouter.showImageGallery(ImageGalleryMode.Pick(target))
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

	private fun showTimelineSettings(timelineId: String) {
		showHome()
		(routerState.value.active.instance as? ProjectRoot.Destination.HomeDestination)
			?.component?.showTimelineSettings(timelineId)
	}

	override fun showTimeLine() {
		router.showTimeLine()
	}

	private fun showTimelineEvent(eventId: Int) {
		scope.launch { navigateToDeepLink(ProjectDeepLink.TimelineEvent(eventId)) }
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

	override fun startProjectSync() {
		if (!canSync) return
		if (_closeRequestHandlers.value.isNotEmpty()) return
		val list = unsavedConfirmations()
		list.add(CloseConfirm.Sync)
		_closeRequestHandlers.update { list }
	}

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

	override fun showImageGallery() = modalRouter.showImageGallery(ImageGalleryMode.Browse)

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

			is SearchResult.Note -> scope.launch { navigateToDeepLink(ProjectDeepLink.Note(result.noteId)) }

			is SearchResult.EncyclopediaEntry -> showEncyclopediaEntry(result.entryDef)

			is SearchResult.TimelineEvent -> scope.launch { navigateToDeepLink(ProjectDeepLink.TimelineEvent(result.eventId)) }
		}
		dismissGlobalSearch()
	}

	override suspend fun navigateToDeepLink(link: ProjectDeepLink): Boolean {
		when (link) {
			is ProjectDeepLink.Scene -> {
				val sceneItem = sceneEditor.getSceneItemFromId(link.sceneId)
				if (sceneItem == null) {
					Napier.w("Deep link skipped: no scene for id ${link.sceneId}")
					return false
				}
				withContext(dispatcherMain) { showEditorScene(sceneItem) }
			}

			is ProjectDeepLink.Note -> {
				if (notesRepository.getNoteById(link.noteId) == null) {
					Napier.w("Deep link skipped: no note for id ${link.noteId}")
					return false
				}
				withContext(dispatcherMain) {
					showNotes()
					(routerState.value.active.instance as? ProjectRoot.Destination.NotesDestination)
						?.component?.showViewNote(link.noteId)
				}
			}

			is ProjectDeepLink.EncyclopediaEntry -> {
				val entryDef = encyclopediaService.findEntryDef(link.entryId)
				if (entryDef == null) {
					Napier.w("Deep link skipped: no encyclopedia entry for id ${link.entryId}")
					return false
				}
				withContext(dispatcherMain) { showEncyclopediaEntry(entryDef) }
			}

			is ProjectDeepLink.TimelineEvent -> {
				if (timeLineRepository.getTimelineEvent(link.eventId) == null) {
					Napier.w("Deep link skipped: no timeline event for id ${link.eventId}")
					return false
				}
				withContext(dispatcherMain) {
					showTimeLine()
					(routerState.value.active.instance as? ProjectRoot.Destination.TimeLineDestination)
						?.component?.showEvent(link.eventId)
				}
			}
		}
		return true
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
			val list = unsavedConfirmations()

			if (
				projectsRepository.isSyncEnabled(projectDef) &&
				syncJournal.shouldAutoSync() &&
				duplicateIdService.unresolved.value.isEmpty()
			) {
				list.add(CloseConfirm.Sync)
			}

			list.add(CloseConfirm.Complete)
			withContext(dispatcherMain) {
				_closeRequestHandlers.update { list }
			}
		}
	}

	/** Ordered set of the unsaved-work confirmations currently needed, scenes first. */
	private fun unsavedConfirmations(): MutableSet<CloseConfirm> {
		val list = mutableSetOf<CloseConfirm>()
		if (hasUnsavedBuffers()) {
			list.add(CloseConfirm.Scenes)
		}
		list.addAll(router.shouldConfirmClose())
		return list
	}

	override fun cancelCloseRequest() {
		_closeRequestHandlers.update { emptySet() }
	}

	override suspend fun discardUnsaved(item: CloseConfirm) {
		when (item) {
			CloseConfirm.Scenes -> withContext(dispatcherDefault) { sceneEditor.discardAllBuffers() }
			CloseConfirm.Notes,
			CloseConfirm.Encyclopedia,
			CloseConfirm.Timeline -> withContext(dispatcherMain) { router.discardUnsaved(item) }

			CloseConfirm.Sync,
			CloseConfirm.Complete -> Unit
		}
	}
}

/**
 * A project root for hosts whose close requests (`project.close`, Home's close button) raise the
 * unsaved-changes confirm through [ProjectRoot.requestClose]. The host closes the project when
 * [ProjectRoot.closeRequestHandlers] reaches [CloseConfirm.Complete].
 */
fun confirmingProjectRootComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	initialDeepLink: ProjectDeepLink? = null,
): ProjectRootComponent {
	lateinit var root: ProjectRootComponent
	root = ProjectRootComponent(
		componentContext = componentContext,
		projectDef = projectDef,
		addMenu = {},
		removeMenu = {},
		onCloseProject = { root.requestClose() },
		initialDeepLink = initialDeepLink,
	)
	return root
}
