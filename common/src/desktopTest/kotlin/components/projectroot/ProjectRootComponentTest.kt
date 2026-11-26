package components.projectroot

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.save_all_toast
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectDisabledPluginsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickResult
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootModalRouter
import com.darkrockstudios.apps.hammer.common.components.projectroot.confirmingProjectRootComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.export.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.ProjectFileRationalizer
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSelectionSetting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheet
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import utils.KeyStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRootComponentTest : ComponentTest() {

	private val projectSyncEnabled = MutableStateFlow(true)
	private val projectsRepository = mockk<ProjectsRepository> {
		every { isSyncEnabled(any()) } answers { projectSyncEnabled.value }
		every { syncEnabledUpdates(any()) } returns projectSyncEnabled
		every { getOwner(any()) } returns null
	}

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var syncJournal: SyncJournal
	private lateinit var projectDataRepository: ProjectDataRepository
	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var protocolMismatchRepository: ProtocolMismatchRepository
	private lateinit var projectFiles: FakeFileSystem
	private lateinit var duplicateIdService: DuplicateIdService
	private lateinit var imagePickRequests: ImagePickRequests
	private lateinit var timeLineRepository: TimeLineRepository
	private lateinit var timelineService: TimelineService

	private var projectClosed = false

	@BeforeEach
	override fun setup() {
		super.setup()

		projectClosed = false

		settingsUpdates = MutableSharedFlow()
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettingsUpdates } returns MutableStateFlow(null)
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns settingsUpdates

		sceneEditor = mockk(relaxed = true)
		syncJournal = mockk(relaxed = true)

		projectFiles = FakeFileSystem()
		duplicateIdService = DuplicateIdService(
			projectDef = projectDef,
			rationalizer = ProjectFileRationalizer(projectDef, projectFiles, createTomlSerializer()),
			syncJournal = syncJournal,
		)

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

		timeLineRepository = mockk(relaxed = true)
		every { timeLineRepository.timelineFlow } returns MutableSharedFlow()
		timelineService = mockk(relaxed = true)
		every { timelineService.timelineFlow(any()) } returns MutableSharedFlow()
		every { timelineService.timelines } returns MutableSharedFlow()
		every { timelineService.orphansFlow } returns MutableSharedFlow()
		every { timelineService.summaries } returns MutableSharedFlow()
		every { timelineService.eventFlow(any()) } returns MutableSharedFlow()
		every { timelineService.subjectUpdates(any()) } returns MutableSharedFlow()
		val sceneRepository = mockk<SceneRepository>(relaxed = true)
		every { sceneRepository.sceneTreeUpdates } returns MutableSharedFlow()

		val tagIndexService = mockk<TagIndexService>(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(mockk(relaxed = true))

		imagePickRequests = ImagePickRequests()
		val imagesService = mockk<ImagesService>(relaxed = true)
		every { imagesService.imagesFlow } returns MutableSharedFlow()

		val spellCheckRepository = mockk<SpellCheckRepository>(relaxed = true)
		every { spellCheckRepository.dictionaryFlow } returns MutableSharedFlow()

		setupComponentKoin(module {
			single<CoroutineScope>(named(APP_SCOPE)) { scope }
			single { globalSettingsStore }
			single { NuxRepository(NuxFilesystemDatasource(FakeFileSystem(), createTomlSerializer()), get()) }
			single { projectsRepository }
			single { ProjectPluginsRepository(ProjectPluginsDatasource(FakeFileSystem(), createTomlSerializer())) }
			single { StoryExporterRegistry(emptyList()) }
			single<ProjectBackupService> { mockk(relaxed = true) }
			single { spellCheckRepository }
			single<FocusModeService> { mockk(relaxed = true) }
			single<UrlLauncher> { mockk(relaxed = true) }
			single<StrRes> { KeyStrRes() }
			single<VersionCheckRepository> { mockk(relaxed = true) }
			single { TimelineSelectionSetting(PropertiesSettings(Properties())) }
			single<AccountTagService> { mockk(relaxed = true) }
			single<PlatformSpellCheckerFactory> {
				mockk { every { availableLocales() } returns emptyList() }
			}
			scope<ProjectDefScope> {
				scoped { ProjectDisabledPluginsUseCase(projectDef, get(), get(), getAll()) }
				scoped { ProjectDataConflictBroker(projectDef) }
				scoped<AutoConfirmReferencesUseCase> { mockk(relaxed = true) }
				scoped<ScrubInvalidReferencesUseCase> { mockk(relaxed = true) }
				scoped { sceneEditor }
				scoped { syncJournal }
				scoped { duplicateIdService }
				scoped { projectDataRepository }
				scoped { encyclopediaService }
				scoped { tagIndexService }
				scoped<SearchProjectUseCase> { mockk(relaxed = true) }
				scoped { projectSynchronizer }
				scoped { statisticsService }
				scoped<ProjectStatisticsUseCase> { mockk(relaxed = true) }
				scoped<ExportStoryUseCase> { mockk(relaxed = true) }
				scoped { notesRepository }
				scoped { timeLineRepository }
				scoped { timelineService }
				scoped { sceneRepository }
				scoped<EncyclopediaRepository> { mockk(relaxed = true) }
				scoped<SceneDraftRepository> { mockk(relaxed = true) }
				scoped { imagePickRequests }
				scoped { imagesService }
			}
		})

		protocolMismatchRepository = org.koin.core.context.GlobalContext.get().get()
	}

	private fun newComponent(
		initialDeepLink: ProjectDeepLink? = null,
	) = ProjectRootComponent(
		componentContext = context,
		projectDef = projectDef,
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
	fun `Timeline deep link opens the event on its own timeline`() = runTest(mainTestDispatcher) {
		val event = TimeLineEvent(id = 7, order = 0, content = "Hayes signs the note of hand", timeline = "a3f09c1e")
		coEvery { timeLineRepository.getTimelineEvent(7) } returns event
		coEvery { timelineService.event(7) } returns event
		coEvery { timelineService.definition("a3f09c1e") } returns TimelineDef(id = "a3f09c1e", name = "Before the evening")

		val comp = newComponent(initialDeepLink = ProjectDeepLink.TimelineEvent(7))
		context.resume()
		advanceUntilIdle()

		val timeline = assertIs<ProjectRoot.Destination.TimeLineDestination>(comp.routerState.value.active.instance).component
		assertEquals(TimelineSelection.Def("a3f09c1e"), timeline.selection.value)
		assertEquals(TimeLine.Config.ViewEventConfig(projectDef, 7), timeline.stack.value.active.configuration)
	}

	@Test
	fun `Timeline deep link to an event whose timeline is gone opens the deleted-timeline bucket`() =
		runTest(mainTestDispatcher) {
			val event = TimeLineEvent(id = 8, order = 0, content = "Stray", timeline = "gone0001")
			coEvery { timeLineRepository.getTimelineEvent(8) } returns event
			coEvery { timelineService.event(8) } returns event
			coEvery { timelineService.definition("gone0001") } returns null

			val comp = newComponent(initialDeepLink = ProjectDeepLink.TimelineEvent(8))
			context.resume()
			advanceUntilIdle()

			val timeline = assertIs<ProjectRoot.Destination.TimeLineDestination>(comp.routerState.value.active.instance).component
			assertEquals(TimelineSelection.Orphans, timeline.selection.value)
			assertEquals(TimeLine.Config.ViewEventConfig(projectDef, 8), timeline.stack.value.active.configuration)
		}

	@Test
	fun `Timeline settings open on the timeline the timeline screen shows`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showDestination(ProjectRoot.DestinationTypes.TimeLine)
		advanceUntilIdle()
		val timeline = assertIs<ProjectRoot.Destination.TimeLineDestination>(comp.routerState.value.active.instance).component
		timeline.selectTimeline(TimelineSelection.Def("a3f09c1e"))

		timeline.showTimelineSettings()
		advanceUntilIdle()

		val home = assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance).component
		val settings = assertIs<ProjectHome.ContentDestination.ProjectSettings>(home.contentRouterState.value.active.instance)
		assertEquals("a3f09c1e", settings.component.timelineSettings.state.value.editingId)
		assertTrue(settings.component.timelineSettings.state.value.editorOpen)
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
	fun `startProjectSync with nothing unsaved requests only the sync`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Sync), comp.closeRequestHandlers.value)
	}

	@Test
	fun `startProjectSync gates on unsaved scenes before the sync`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		advanceUntilIdle()

		assertEquals(listOf(CloseConfirm.Scenes, CloseConfirm.Sync), comp.closeRequestHandlers.value.toList())

		comp.closeRequestDealtWith(CloseConfirm.Scenes)
		assertEquals(setOf(CloseConfirm.Sync), comp.closeRequestHandlers.value)
	}

	@Test
	fun `startProjectSync does nothing for local-only projects`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns false
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		advanceUntilIdle()

		assertTrue(comp.closeRequestHandlers.value.isEmpty())
		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Home sync action goes through the gate`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val home = assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
		home.component.startProjectSync()
		advanceUntilIdle()

		assertEquals(listOf(CloseConfirm.Scenes, CloseConfirm.Sync), comp.closeRequestHandlers.value.toList())
		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `startProjectSync leaves a pending close request alone`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.requestClose()
		advanceUntilIdle()
		val pending = comp.closeRequestHandlers.value

		comp.startProjectSync()
		advanceUntilIdle()

		assertEquals(pending, comp.closeRequestHandlers.value)
		assertTrue(CloseConfirm.Complete in comp.closeRequestHandlers.value)
	}

	@Test
	fun `Sync dialog closing clears a pending sync request`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.startProjectSync()
		comp.showProjectSync()
		advanceUntilIdle()
		assertEquals(setOf(CloseConfirm.Sync), comp.closeRequestHandlers.value)

		comp.dismissProjectSync()
		advanceUntilIdle()

		assertTrue(comp.closeRequestHandlers.value.isEmpty())
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

	private fun writeDuplicateEntryFiles() {
		val dir = projectDef.path.toOkioPath() / "encyclopedia" / "person"
		projectFiles.createDirectories(dir)
		projectFiles.write(dir / "person~5~Bob.toml") { writeUtf8("one") }
		projectFiles.write(dir / "person~5~Bob.sync-conflict-x.toml") { writeUtf8("two") }
	}

	@Test
	fun `Unresolved duplicate files surface their dialog on open`() = runTest(mainTestDispatcher) {
		writeDuplicateEntryFiles()
		duplicateIdService.rationalizeOnOpen()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val modal = comp.modalRouterState.value.child?.instance
		assertIs<ProjectRoot.ModalDestination.DuplicateIdsModal>(modal)
		assertEquals(5, modal.component.state.value.current?.id)

		modal.component.decideLater()
		assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Request close skips the auto sync while duplicate files are unresolved`() = runTest(mainTestDispatcher) {
		coEvery { syncJournal.shouldAutoSync() } returns true
		writeDuplicateEntryFiles()
		duplicateIdService.rationalizeOnOpen()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.requestClose()
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Complete), comp.closeRequestHandlers.value)
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
	fun `No sync action, sync request or close sync when project sync is off`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns false
		coEvery { syncJournal.shouldAutoSync() } returns true
		projectSyncEnabled.value = false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		assertFalse(comp.actions.isLive(HammerActions.ProjectSync.id))

		comp.startProjectSync()
		advanceUntilIdle()
		assertTrue(comp.closeRequestHandlers.value.isEmpty())

		comp.requestClose()
		advanceUntilIdle()
		assertEquals(setOf(CloseConfirm.Complete), comp.closeRequestHandlers.value)
	}

	@Test
	fun `The sync action follows the project sync setting`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		assertTrue(comp.actions.isLive(HammerActions.ProjectSync.id))

		projectSyncEnabled.value = false
		advanceUntilIdle()
		assertFalse(comp.actions.isLive(HammerActions.ProjectSync.id))

		projectSyncEnabled.value = true
		advanceUntilIdle()
		assertTrue(comp.actions.isLive(HammerActions.ProjectSync.id))
	}

	@Test
	fun `The image gallery action opens the gallery to browse`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.invoke(HammerActions.ProjectGallery.id)
		advanceUntilIdle()

		assertTrue(handled)
		assertEquals(
			ProjectRootModalRouter.Config.ImageGallery(ImageGalleryMode.Browse),
			comp.modalRouterState.value.child?.configuration,
		)
		assertIs<ProjectRoot.ModalDestination.ImageGalleryModal>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Mod+3 shows the Notes destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3))
		advanceUntilIdle()

		assertTrue(handled)
		assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `Mod+1 through Mod+5 reach every destination in nav rail order`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val digits = listOf(ChordKey.Digit1, ChordKey.Digit2, ChordKey.Digit3, ChordKey.Digit4, ChordKey.Digit5)

		val reached = (digits.drop(1) + digits.first()).associateWith { digit ->
			comp.actions.dispatch(KeyChord.mod(digit))
			advanceUntilIdle()
			comp.routerState.value.active.instance.getLocationType()
		}

		assertEquals(ProjectRoot.DestinationTypes.entries.toList(), digits.map { reached[it] })
	}

	@Test
	fun `Mod+Shift+F opens global search`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.F, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		assertIs<ProjectRoot.ModalDestination.GlobalSearchModal>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `F3 starts a sync for a server synchronized project`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns true
		every { sceneEditor.hasDirtyBuffers() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.bare(ChordKey.F3))
		advanceUntilIdle()

		assertTrue(handled)
		assertEquals(setOf(CloseConfirm.Sync), comp.closeRequestHandlers.value)
	}

	@Test
	fun `F3 is not handled for a local-only project`() = runTest(mainTestDispatcher) {
		every { syncJournal.isServerSynchronized() } returns false

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(comp.actions.dispatch(KeyChord.bare(ChordKey.F3)))
		assertTrue(comp.closeRequestHandlers.value.isEmpty())
	}

	@Test
	fun `Mod+Shift+S saves every buffer and says so`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		val toasts = mutableListOf<ToastMessage>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { comp.toast.collect { toasts += it } }
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.S, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		coVerify { sceneEditor.storeAllBuffers() }
		assertEquals(listOf<ToastMessage>(ToastMessage.Resource(Res.string.save_all_toast)), toasts)
	}

	@Test
	fun `Mod+Shift+W asks to close the project`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.W, shift = true))

		assertTrue(handled)
		assertTrue(projectClosed)
	}

	@Test
	fun `Mod+Shift+W with a dirty buffer raises the unsaved confirm on a confirming root`() =
		runTest(mainTestDispatcher) {
			every { sceneEditor.hasDirtyBuffers() } returns true
			coEvery { syncJournal.shouldAutoSync() } returns false
			val comp = confirmingProjectRootComponent(context, projectDef)
			context.resume()
			advanceUntilIdle()

			val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.W, shift = true))
			advanceUntilIdle()

			assertTrue(handled)
			assertEquals(listOf(CloseConfirm.Scenes, CloseConfirm.Complete), comp.closeRequestHandlers.value.toList())
		}

	@Test
	fun `Closing from Home on a confirming root raises the unsaved confirm`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns false
		val comp = confirmingProjectRootComponent(context, projectDef)
		context.resume()
		advanceUntilIdle()
		val home = assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)

		home.component.closeProject()
		advanceUntilIdle()

		assertEquals(listOf(CloseConfirm.Scenes, CloseConfirm.Complete), comp.closeRequestHandlers.value.toList())
	}

	@Test
	fun `Root actions stop answering once the project is stopped`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		context.stop()
		advanceUntilIdle()

		assertFalse(comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3)))
		assertFalse(comp.actions.dispatch(KeyChord.mod(ChordKey.W, shift = true)))
		assertTrue(comp.actions.live().isEmpty())
		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
		assertFalse(projectClosed)
	}

	@Test
	fun `Mod+N with focus mode open does nothing`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showDestination(ProjectRoot.DestinationTypes.Notes)
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.N))
		advanceUntilIdle()

		assertFalse(handled)
		val notes = assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
		assertIs<Notes.Destination.BrowseNotesDestination>(notes.component.stack.value.active.instance)
	}

	@Test
	fun `global actions still run over focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.S, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		coVerify { sceneEditor.storeAllBuffers() }
	}

	@Test
	fun `closing the modal restores module actions`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showDestination(ProjectRoot.DestinationTypes.Notes)
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()

		comp.dismissFocusMode()
		advanceUntilIdle()
		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.N))
		advanceUntilIdle()

		assertTrue(handled)
		val notes = assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
		assertIs<Notes.Destination.CreateNoteDestination>(notes.component.stack.value.active.instance)
	}

	@Test
	fun `Mod+3 with a close confirm showing does not navigate`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns false
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.requestClose()
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3))
		advanceUntilIdle()

		assertFalse(handled)
		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
		assertTrue(comp.actions.hasOverlay())
	}

	@Test
	fun `a close confirm still blocks Mod+3 after the window stops and starts`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns false
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.requestClose()
		advanceUntilIdle()

		context.stop()
		context.resume()
		advanceUntilIdle()
		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3))
		advanceUntilIdle()

		assertFalse(handled)
		assertIs<ProjectRoot.Destination.HomeDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `Mod+Shift+Enter leaves focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Enter, shift = true))
		advanceUntilIdle()

		assertTrue(handled)
		val modal = comp.modalRouterState.value.child?.instance
		assertTrue(modal == null || modal is ProjectRoot.ModalDestination.None)
		assertFalse(comp.actions.hasOverlay())
	}

	private fun ProjectRoot.palette() =
		assertIs<ProjectRoot.CommandOverlay.PaletteOverlay>(commandOverlayState.value.child?.instance).component

	private fun ProjectRoot.commandOverlayClosed(): Boolean =
		commandOverlayState.value.child?.instance.let { it == null || it is ProjectRoot.CommandOverlay.None }

	private fun ProjectRoot.cheatSheet() =
		assertIs<ProjectRoot.CommandOverlay.CheatSheetOverlay>(commandOverlayState.value.child?.instance).component

	/** What the overlay's UI does once its exit animation has played. */
	private fun ProjectRoot.finishCommandOverlayExit() {
		when (val overlay = commandOverlayState.value.child?.instance) {
			is ProjectRoot.CommandOverlay.PaletteOverlay -> overlay.component.onDismissed()
			is ProjectRoot.CommandOverlay.CheatSheetOverlay -> overlay.component.onDismissed()
			else -> {}
		}
	}

	@Test
	fun `a dismissed palette stays up for its exit, then the slot clears`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()
		val palette = comp.palette()

		comp.dismissCommandOverlay()
		val shownDuringExit = comp.commandOverlayState.value.child?.instance
		comp.finishCommandOverlayExit()

		assertFalse(palette.state.value.open)
		assertIs<ProjectRoot.CommandOverlay.PaletteOverlay>(shownDuringExit)
		assertTrue(comp.commandOverlayClosed())
		assertFalse(comp.actions.hasOverlay())
	}

	@Test
	fun `Esc during the palette exit over focus mode keeps focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		comp.actions.dismissTopOverlay()
		val secondEsc = comp.actions.dismissTopOverlay()
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		assertTrue(secondEsc)
		assertTrue(comp.commandOverlayClosed())
		assertIs<ProjectRoot.ModalDestination.FocusModeModal>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `Mod+K while the palette plays its exit opens a fresh one`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()
		val first = comp.palette()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))

		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		val second = comp.palette()
		assertTrue(first !== second)
		assertTrue(second.state.value.open)
		assertTrue(HammerActions.NavNotes.id in second.state.value.entries.map { it.id })
	}

	@Test
	fun `F1 while the palette plays its exit opens a sheet of what is live below it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()
		comp.dismissCommandOverlay()

		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		val navNotes = comp.cheatSheet().state.value.sections.flatMap { it.rows }.single { it.id == HammerActions.NavNotes.id }
		assertEquals(CheatSheet.Reach.Live, navNotes.reach)
	}

	@Test
	fun `the cheat sheet remembers Show all between openings`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()
		comp.cheatSheet().setShowAll(false)
		comp.dismissCommandOverlay()
		comp.finishCommandOverlayExit()

		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		assertFalse(comp.cheatSheet().state.value.showAll)
	}

	@Test
	fun `Mod+K opens the palette and F1 the cheat sheet`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val paletteOpened = comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()
		val paletteShown = comp.commandOverlayState.value.child?.instance
		comp.dismissCommandOverlay()
		val sheetOpened = comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		assertTrue(paletteOpened)
		assertIs<ProjectRoot.CommandOverlay.PaletteOverlay>(paletteShown)
		assertTrue(sheetOpened)
		assertIs<ProjectRoot.CommandOverlay.CheatSheetOverlay>(comp.commandOverlayState.value.child?.instance)
	}

	@Test
	fun `Mod+K over focus mode opens the palette and keeps focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showDestination(ProjectRoot.DestinationTypes.Notes)
		advanceUntilIdle()
		val itemNewLiveBefore = comp.actions.isLive(HammerActions.ItemNew.id)
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		assertTrue(itemNewLiveBefore)
		assertTrue(handled)
		assertIs<ProjectRoot.ModalDestination.FocusModeModal>(comp.modalRouterState.value.child?.instance)
		val ids = comp.palette().state.value.entries.map { it.id }
		assertFalse(HammerActions.ItemNew.id in ids)
		assertTrue(HammerActions.ProjectSaveAll.id in ids)
	}

	@Test
	fun `Esc closes the palette over focus mode and keeps focus mode`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		val closed = comp.actions.dismissTopOverlay()
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		assertTrue(closed)
		assertTrue(comp.commandOverlayClosed())
		assertIs<ProjectRoot.ModalDestination.FocusModeModal>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `a global action run from the palette over focus mode works`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showFocusMode(sceneItem())
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		comp.palette().run(HammerActions.ProjectSaveAll.id)
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		coVerify { sceneEditor.storeAllBuffers() }
		assertTrue(comp.commandOverlayClosed())
		assertIs<ProjectRoot.ModalDestination.FocusModeModal>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `a module action run from the palette runs once the palette is gone`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showDestination(ProjectRoot.DestinationTypes.Notes)
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		comp.palette().run(HammerActions.ItemNew.id)
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		assertTrue(comp.commandOverlayClosed())
		val notes = assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
		assertIs<Notes.Destination.CreateNoteDestination>(notes.component.stack.value.active.instance)
	}

	@Test
	fun `Mod+K with the palette open closes it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		assertTrue(handled)
		assertTrue(comp.commandOverlayClosed())
		assertFalse(comp.actions.hasOverlay())
	}

	@Test
	fun `F1 with the cheat sheet open closes it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		val handled = comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()
		comp.finishCommandOverlayExit()

		assertTrue(handled)
		assertTrue(comp.commandOverlayClosed())
		assertFalse(comp.actions.hasOverlay())
	}

	@Test
	fun `F1 with the palette open switches to the cheat sheet`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		val navNotes = comp.cheatSheet().state.value.sections.flatMap { it.rows }.single { it.id == HammerActions.NavNotes.id }
		assertEquals(CheatSheet.Reach.Live, navNotes.reach)
	}

	@Test
	fun `Mod+K over the cheat sheet switches to a palette of what is live below it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.actions.dispatch(KeyChord.bare(ChordKey.F1))
		advanceUntilIdle()

		comp.actions.dispatch(KeyChord.mod(ChordKey.K))
		advanceUntilIdle()

		val ids = comp.palette().state.value.entries.map { it.id }
		assertTrue(HammerActions.NavNotes.id in ids)
	}

	@Test
	fun `cancelling the close confirm restores navigation`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true
		coEvery { syncJournal.shouldAutoSync() } returns false
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.requestClose()
		advanceUntilIdle()

		comp.cancelCloseRequest()
		val handled = comp.actions.dispatch(KeyChord.mod(ChordKey.Digit3))
		advanceUntilIdle()

		assertTrue(handled)
		assertIs<ProjectRoot.Destination.NotesDestination>(comp.routerState.value.active.instance)
	}

	@Test
	fun `An image pick request opens the gallery to pick, and closing it answers the request`() =
		runTest(mainTestDispatcher) {
			val results = mutableListOf<ImagePickResult>()
			backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
				imagePickRequests.results.collect { results += it }
			}
			val target = ImagePickTarget.EntryImage(entryId = 4)
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			imagePickRequests.request(target)
			advanceUntilIdle()

			val modal = assertIs<ProjectRoot.ModalDestination.ImageGalleryModal>(
				comp.modalRouterState.value.child?.instance
			)
			assertEquals(ImageGalleryMode.Pick(target), modal.component.state.value.mode)

			modal.component.dismiss()
			advanceUntilIdle()

			assertEquals(listOf(ImagePickResult(target, null)), results)
			assertIs<ProjectRoot.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
		}

	@Test
	fun `Unsaved buffer queries delegate to the scene editor`() = runTest(mainTestDispatcher) {
		every { sceneEditor.hasDirtyBuffers() } returns true

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.hasUnsavedBuffers())
		assertTrue(comp.unsavedBuffers.value)

		comp.storeDirtyBuffers()
		coVerify { sceneEditor.storeAllBuffers() }
	}

	@Test
	fun `Discarding unsaved scenes reverts the editor buffers`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.discardUnsaved(CloseConfirm.Scenes)

		verify { sceneEditor.discardAllBuffers() }
	}

	@Test
	fun `Discarding sync or completion touches nothing`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.discardUnsaved(CloseConfirm.Sync)
		comp.discardUnsaved(CloseConfirm.Complete)

		verify(exactly = 0) { sceneEditor.discardAllBuffers() }
	}
}
