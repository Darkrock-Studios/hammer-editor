package components.projectroot

import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRootComponentTest : ComponentTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var syncJournal: SyncJournal
	private lateinit var projectDataRepository: ProjectDataRepository
	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var protocolMismatchRepository: ProtocolMismatchRepository

	private val addedMenus = mutableListOf<MenuDescriptor>()
	private val removedMenuIds = mutableListOf<String>()
	private var projectClosed = false

	@BeforeEach
	override fun setup() {
		super.setup()

		addedMenus.clear()
		removedMenuIds.clear()
		projectClosed = false

		settingsUpdates = MutableSharedFlow()
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns settingsUpdates

		sceneEditor = mockk(relaxed = true)
		syncJournal = mockk(relaxed = true)

		encyclopediaService = mockk(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableSharedFlow()

		projectDataRepository = mockk(relaxed = true)
		every { projectDataRepository.state } returns MutableStateFlow(null)

		val statisticsService = mockk<StatisticsService>(relaxed = true)
		every { statisticsService.statsFlow } returns MutableSharedFlow()
		every { statisticsService.isDirty } returns MutableStateFlow(false)
		every { statisticsService.isCalculating } returns MutableStateFlow(false)

		val projectSynchronizer = mockk<ClientProjectSynchronizer>(relaxed = true)
		every { projectSynchronizer.syncCompleteEvent } returns Channel()

		val notesRepository = mockk<NotesRepository>(relaxed = true)
		every { notesRepository.notesListFlow } returns MutableSharedFlow()

		val timeLineRepository = mockk<TimeLineRepository>(relaxed = true)
		every { timeLineRepository.timelineFlow } returns MutableSharedFlow()

		val tagIndexService = mockk<TagIndexService>(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(mockk(relaxed = true))

		val spellCheckRepository = mockk<SpellCheckRepository>(relaxed = true)
		every { spellCheckRepository.dictionaryFlow } returns MutableSharedFlow()

		setupComponentKoin(module {
			single<CoroutineScope>(named(APP_SCOPE)) { scope }
			single { globalSettingsStore }
			single<ProjectBackupRepository> { mockk(relaxed = true) }
			single { spellCheckRepository }
			single<FocusModeService> { mockk(relaxed = true) }
			single<UrlLauncher> { mockk(relaxed = true) }
			single<VersionCheckRepository> { mockk(relaxed = true) }
			scope<ProjectDefScope> {
				scoped { ProjectDataConflictBroker(projectDef) }
				scoped<AutoConfirmReferencesUseCase> { mockk(relaxed = true) }
				scoped<ScrubInvalidReferencesUseCase> { mockk(relaxed = true) }
				scoped { sceneEditor }
				scoped { syncJournal }
				scoped { projectDataRepository }
				scoped { encyclopediaService }
				scoped { tagIndexService }
				scoped<SearchProjectUseCase> { mockk(relaxed = true) }
				scoped { projectSynchronizer }
				scoped { statisticsService }
				scoped<ExportStoryUseCase> { mockk(relaxed = true) }
				scoped { notesRepository }
				scoped { timeLineRepository }
				scoped<SceneDraftRepository> { mockk(relaxed = true) }
			}
		})

		protocolMismatchRepository = org.koin.core.context.GlobalContext.get().get()
	}

	private fun newComponent(
		initialDeepLink: ProjectDeepLink? = null,
	) = ProjectRootComponent(
		componentContext = context,
		projectDef = projectDef,
		addMenu = { addedMenus.add(it) },
		removeMenu = { removedMenuIds.add(it) },
		onCloseProject = { projectClosed = true },
		initialDeepLink = initialDeepLink,
	)

	private fun sceneItem(id: Int = 1) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = "Scene $id",
		order = 0,
	)

	@Test
	fun `Initial destination is Home and is at root`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
		assertTrue(comp.isAtRoot())
		assertTrue(comp.backEnabled.value)
	}

	@Test
	fun `Editor initial screen starts on the editor with Home beneath`() = runTest(mainTestDispatcher) {
		every { globalSettingsStore.globalSettings } returns GlobalSettings(
			projectsDirectory = "/projects",
			initialProjectScreen = InitialProjectScreen.Editor,
		)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectRoot.Destination.EditorDestination>(comp.routerState.value.active.instance)
		assertFalse(comp.isAtRoot())

		comp.onBack()
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `showDestination navigates between all top level screens`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showDestination(ProjectRoot.DestinationTypes.Editor)
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.EditorDestination>(comp.routerState.value.active.instance)
		assertFalse(comp.backEnabled.value)

		comp.showDestination(ProjectRoot.DestinationTypes.Notes)
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)

		comp.showDestination(ProjectRoot.DestinationTypes.Encyclopedia)
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.EncyclopediaDestination>(comp.routerState.value.active.instance)

		comp.showDestination(ProjectRoot.DestinationTypes.TimeLine)
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.TimeLineDestination>(comp.routerState.value.active.instance)

		comp.showDestination(ProjectRoot.DestinationTypes.Home)
		advanceUntilIdle()
		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `Scene deep link opens the editor with the scene shown`() = runTest(mainTestDispatcher) {
		val scene = sceneItem(7)
		every { sceneEditor.getSceneItemFromId(7) } returns scene

		val comp = newComponent(initialDeepLink = ProjectDeepLink.Scene(7))
		context.resume()
		advanceUntilIdle()

		val active = comp.routerState.value.active.instance
		assertIs<ProjectRoot.Destination.EditorDestination>(active)
		assertTrue(active.component.isDetailShown())
	}

	@Test
	fun `Scene deep link for a missing scene stays on Home`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneItemFromId(99) } returns null

		val comp = newComponent(initialDeepLink = ProjectDeepLink.Scene(99))
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `Note deep link opens the notes destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent(initialDeepLink = ProjectDeepLink.Note(5))
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `Encyclopedia deep link for a missing entry stays on Home`() = runTest(mainTestDispatcher) {
		every { encyclopediaService.findEntryDef(42) } returns null

		val comp = newComponent(initialDeepLink = ProjectDeepLink.EncyclopediaEntry(42))
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `Project sync modal opens and dismisses`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showProjectSync()
		advanceUntilIdle()
		assertIs<ProjectRoot.ModalDestination.ProjectSync>(comp.modalRouterState.value.child?.instance)
		assertFalse(comp.isAtRoot())

		comp.dismissProjectSync()
		advanceUntilIdle()
		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `startProjectSync opens the modal for server synchronized projects`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		advanceUntilIdle()

		assertIs<ProjectRoot.ModalDestination.ProjectSync>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `startProjectSync does nothing for local-only projects`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		advanceUntilIdle()

		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Global search modal opens and dismisses`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showGlobalSearch()
		advanceUntilIdle()
		assertIs<ProjectRoot.ModalDestination.GlobalSearchModal>(comp.modalRouterState.value.child?.instance)

		comp.dismissGlobalSearch()
		advanceUntilIdle()
		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Focus mode opens as a modal and reopens the scene in the editor on dismiss`() =
		runTest(mainTestDispatcher) {
			val scene = sceneItem(3)

			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showEditor()
			advanceUntilIdle()

			comp.showFocusMode(scene)
			advanceUntilIdle()
			assertIs<ProjectRoot.ModalDestination.FocusModeModal>(comp.modalRouterState.value.child?.instance)

			comp.dismissFocusMode()
			advanceUntilIdle()
			assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)

			val active = comp.routerState.value.active.instance
			assertIs<ProjectRoot.Destination.EditorDestination>(active)
			assertTrue(active.component.isDetailShown())
		}

	@Test
	fun `Protocol mismatch notification surfaces the modal`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		protocolMismatchRepository.notifyMismatch(clientProtocolVersion = 8, serverProtocolVersion = 9)
		advanceUntilIdle()

		val modal = comp.modalRouterState.value.child?.instance
		assertIs<ProjectRoot.ModalDestination.ProtocolMismatchModal>(modal)
	}

	@Test
	fun `Request close collects confirmation requirements`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.requestClose()
		advanceUntilIdle()

		val handlers = comp.closeRequestHandlers.value
		assertTrue(CloseConfirm.Scenes in handlers)
		assertTrue(CloseConfirm.Sync in handlers)
		assertTrue(CloseConfirm.Complete in handlers)

		comp.closeRequestDealtWith(CloseConfirm.Scenes)
		assertFalse(CloseConfirm.Scenes in comp.closeRequestHandlers.value)

		comp.cancelCloseRequest()
		assertTrue(comp.closeRequestHandlers.value.isEmpty())
	}

	@Test
	fun `Request close without dirty state only requires completion`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns false
		coEvery { syncJournal.shouldAutoSync() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.requestClose()
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Complete), comp.closeRequestHandlers.value)
	}

	@Test
	fun `Nav rail state follows settings updates`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		assertFalse(comp.navRailState.value.expanded)

		settingsUpdates.emit(GlobalSettings(projectsDirectory = "/projects", navRailExpanded = true))
		advanceUntilIdle()

		assertTrue(comp.navRailState.value.expanded)
	}

	@Test
	fun `Toggling the nav rail persists the flipped state to settings`() = runTest(mainTestDispatcher) {
		val settingsTransform = slot<(GlobalSettings) -> GlobalSettings>()
		coEvery { globalSettingsStore.updateSettings(capture(settingsTransform)) } returns Unit

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.toggleNavRailExpanded()
		advanceUntilIdle()

		val collapsed = GlobalSettings(projectsDirectory = "/projects", navRailExpanded = false)
		assertTrue(settingsTransform.captured(collapsed).navRailExpanded)

		val expanded = collapsed.copy(navRailExpanded = true)
		assertFalse(settingsTransform.captured(expanded).navRailExpanded)
	}

	@Test
	fun `Sync menu is added on start for server synchronized projects`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true

		newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(addedMenus.any { it.id == ProjectRootComponent.SYNC_MENU_ID })

		context.stop()
		advanceUntilIdle()
		assertTrue(ProjectRootComponent.SYNC_MENU_ID in removedMenuIds)
	}

	@Test
	fun `No sync menu for local-only projects`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns false

		newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(addedMenus.isEmpty())
	}

	@Test
	fun `Unsaved buffer queries delegate to the scene editor`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.hasUnsavedBuffers())

		comp.storeDirtyBuffers()
		coVerify { sceneEditor.storeAllBuffers() }
	}
}
