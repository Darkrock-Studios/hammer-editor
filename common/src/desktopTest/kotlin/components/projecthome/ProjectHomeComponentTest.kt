package components.projecthome

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.data.export.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHomeComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportFileName
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExporterSource
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.EntryAppearance
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatistics
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsUseCase
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityRef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_failure
import com.darkrockstudios.apps.hammer.project_home_action_backup_toast_success
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.BufferedSink
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectHomeComponentTest : ComponentTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var projectBackupService: ProjectBackupService
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var exportStoryUseCase: ExportStoryUseCase
	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var synchronizer: ClientProjectSynchronizer
	private lateinit var statisticsService: StatisticsService
	private lateinit var tagIndexService: TagIndexService
	private lateinit var referenceIndexService: ReferenceIndexService
	private lateinit var pluginFormats: FakeExporterSource
	private lateinit var projectPlugins: ProjectPluginsRepository

	private lateinit var statsFlow: MutableSharedFlow<ProjectStatistics>
	private lateinit var isDirtyFlow: MutableStateFlow<Boolean>
	private lateinit var isCalculatingFlow: MutableStateFlow<Boolean>
	private lateinit var tagIndexFlow: MutableStateFlow<TagIndex>
	private lateinit var syncCompleteChannel: Channel<Boolean>
	private lateinit var projectDataState: MutableStateFlow<StoredProjectData?>
	private lateinit var imagesService: ImagesService
	private lateinit var imageChangedFlow: MutableSharedFlow<Int>
	private val imageFiles = mutableMapOf<Int, ImageFile>()

	private var loadStatisticsCalls = 0

	private var syncShown = false
	private var globalSearchShown = false
	private var imageGalleryShown = false
	private var searchedTag: String? = null
	private var shownScene: SceneItem? = null
	private var shownEntry: EntryDef? = null
	private var projectClosed = false

	@BeforeEach
	override fun setup() {
		super.setup()

		loadStatisticsCalls = 0
		syncShown = false
		globalSearchShown = false
		imageGalleryShown = false
		searchedTag = null
		shownScene = null
		shownEntry = null
		projectClosed = false

		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()
		every { globalSettingsStore.serverSettings } returns null

		projectBackupService = mockk()

		sceneEditor = mockk()
		coEvery { sceneEditor.getMetadata() } returns ProjectMetadata(
			Info(created = Instant.parse("2024-01-15T12:00:00Z"))
		)
		every { sceneEditor.getSceneTree() } returns buildSceneTree()

		exportStoryUseCase = mockk()
		encyclopediaService = mockk()

		syncCompleteChannel = Channel()
		synchronizer = mockk()
		every { synchronizer.syncCompleteEvent } returns syncCompleteChannel

		statsFlow = MutableSharedFlow()
		isDirtyFlow = MutableStateFlow(false)
		isCalculatingFlow = MutableStateFlow(false)
		statisticsService = mockk()
		every { statisticsService.statsFlow } returns statsFlow
		every { statisticsService.isDirty } returns isDirtyFlow
		every { statisticsService.isCalculating } returns isCalculatingFlow
		coEvery { statisticsService.loadStatistics(any()) } coAnswers { loadStatisticsCalls++; stats() }
		coEvery { statisticsService.recalculateStatistics(any()) } returns stats()

		tagIndexFlow = MutableStateFlow(TagIndex.EMPTY)
		tagIndexService = mockk()
		every { tagIndexService.tagIndex } returns tagIndexFlow

		referenceIndexService = mockk(relaxed = true)

		projectDataState = MutableStateFlow(null)
		imageFiles.clear()
		imageChangedFlow = MutableSharedFlow(extraBufferCapacity = 8)
		imagesService = mockk()
		every { imagesService.imageChangedFlow } returns imageChangedFlow
		coEvery { imagesService.loadImageFile(any()) } answers { imageFiles[firstArg()] }

		pluginFormats = FakeExporterSource()
		projectPlugins = ProjectPluginsRepository(ProjectPluginsDatasource(FakeFileSystem(), createTomlSerializer()))

		setupComponentKoin(module {
			single { globalSettingsStore }
			single { StoryExporterRegistry(emptyList(), listOf(pluginFormats)) }
			single { projectPlugins }
			single { projectBackupService }
			single<AccountTagService> { mockk(relaxed = true) }
			single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob()) }
			single<PlatformSpellCheckerFactory> {
				mockk { every { availableLocales() } returns emptyList() }
			}
			scope<ProjectDefScope> {
				scoped { sceneEditor }
				scoped { exportStoryUseCase }
				scoped { encyclopediaService }
				scoped { synchronizer }
				scoped { statisticsService }
				scoped { tagIndexService }
				scoped { referenceIndexService }
				scoped { ProjectStatisticsUseCase(statisticsService, referenceIndexService) }
				scoped<ProjectDataRepository> {
					mockk(relaxed = true) { every { state } returns projectDataState }
				}
				scoped { imagesService }
				scoped { ImagePickRequests() }
			}
		})
	}

	private class FakeExporter(override val formatId: String, override val fileExtension: String) : StoryExporter {
		override val mimeType = "text/plain"
		override fun render(sink: BufferedSink, input: ExportInput) = Unit
	}

	/** Formats of plugins that come and go, each owned by the plugin its id is prefixed with. */
	private class FakeExporterSource : ExporterSource {
		val formats = MutableStateFlow<List<StoryExporter>>(emptyList())
		override val changes = formats.map { }
		override fun exporters(disabledPlugins: Set<String>): List<StoryExporter> =
			formats.value.filterNot { it.formatId.substringBefore('.') in disabledPlugins }
	}

	private fun ProjectHomeComponent.rememberFormat(format: String) {
		beginProjectExport()
		val options = state.value.exportOptions.copy(format = format)
		updateExportOptions(options)
		confirmExportDialog(options)
		endProjectExport()
		exportDialogDismissed()
	}

	private fun newComponent() = ProjectHomeComponent(
		componentContext = context,
		projectDef = projectDef,
		requestProjectSync = { syncShown = true },
		onShowGlobalSearch = { globalSearchShown = true },
		onShowGlobalSearchForTag = { searchedTag = it },
		onShowImageGallery = { imageGalleryShown = true },
		onShowScene = { shownScene = it },
		onShowEntry = { shownEntry = it },
		onCloseProject = { projectClosed = true },
	)

	private fun scene(id: Int, name: String, type: SceneItem.Type = SceneItem.Type.Scene) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	// Scene 1, Group 2 [Scene 3, Scene 4, Group 5 [Scene 6]], Scene 7
	private fun buildSceneTree(): ImmutableTree<SceneItem> {
		val tree = Tree<SceneItem>()
		val root = TreeNode(scene(0, "", SceneItem.Type.Root))
		root.addChild(TreeNode(scene(1, "Scene 1")))
		val group = TreeNode(scene(2, "Group 2", SceneItem.Type.Group))
		group.addChild(TreeNode(scene(3, "Scene 3")))
		group.addChild(TreeNode(scene(4, "Scene 4")))
		val nested = TreeNode(scene(5, "Group 5", SceneItem.Type.Group))
		nested.addChild(TreeNode(scene(6, "Scene 6")))
		group.addChild(nested)
		root.addChild(group)
		root.addChild(TreeNode(scene(7, "Scene 7")))
		tree.setRoot(root)
		return tree.toImmutableTree()
	}

	private fun stats() = ProjectStatistics(
		numberOfScenes = 4,
		totalWords = 1200,
		wordsByChapter = mapOf(1 to 800, 2 to 400),
		encyclopediaEntriesByType = mapOf("PERSON" to 2, "PLACE" to 1),
		longestSceneId = 7,
		longestSceneName = "The Long One",
		longestSceneWords = 600,
		lastEditedSceneId = 3,
		lastEditedSceneName = "Fresh Ink",
		numberOfNotes = 5,
		numberOfTimelineEvents = 2,
		dailyWordTotals = mapOf("2026-07-01" to 500, "2026-07-02" to 700),
		wordsPerDevice = mapOf("Desktop" to 1200),
		wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
		lastCalculated = Instant.parse("2026-07-02T10:00:00Z"),
		schemaVersion = ProjectStatistics.CURRENT_SCHEMA_VERSION,
	)

	@Test
	fun `Stats emissions populate the dashboard state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		statsFlow.emit(stats())
		advanceUntilIdle()

		val state = comp.state.value
		assertEquals(4, state.numberOfScenes)
		assertEquals(1200, state.totalWords)
		assertEquals(300, state.averageWordsPerScene)
		assertEquals(mapOf(EntryType.PERSON to 2, EntryType.PLACE to 1), state.encyclopediaEntriesByType)
		assertEquals("The Long One", state.longestSceneName)
		assertEquals("Fresh Ink", state.lastEditedSceneName)
		assertEquals(
			mapOf(LocalDate(2026, 7, 1) to 500, LocalDate(2026, 7, 2) to 700),
			state.dailyWordTotals
		)
		assertEquals(WordCountGoal(WordCountGoal.Cadence.DAY, 500), state.wordCountGoal)
		assertEquals(mapOf("Desktop" to 1200), state.wordsPerDevice)
		assertFalse(state.isLoadingStats)
		assertFalse(state.hasServer)
		assertTrue(state.created.contains("24"), "Created date should be formatted from project metadata")
	}

	@Test
	fun `Server settings are reflected as hasServer`() = runTest(mainTestDispatcher) {
		every { globalSettingsStore.serverSettings } returns mockk<ServerSettings>()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		statsFlow.emit(stats())
		advanceUntilIdle()

		assertTrue(comp.state.value.hasServer)
	}

	@Test
	fun `Export dialog walks through begin confirm and cancel`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		assertTrue(comp.state.value.showExportDialog)

		val options = ExportOptions(treatTopLevelAsChapters = false, format = BuiltInExportFormat.PDF)
		comp.confirmExportDialog(options)
		assertEquals(options, comp.state.value.exportOptions)
		assertTrue(comp.state.value.showExportFilePicker)
		assertTrue(comp.state.value.showExportDialog, "Dialog stays open through the file picker")

		comp.cancelExportDialog()
		assertFalse(comp.state.value.showExportDialog)
	}

	@Test
	fun `beginProjectExport flattens the scene tree into exportable scenes`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		advanceUntilIdle()

		val expected = listOf(
			ExportableScene(id = 1, name = "Scene 1", isGroup = false, depth = 0),
			ExportableScene(id = 2, name = "Group 2", isGroup = true, depth = 0),
			ExportableScene(id = 3, name = "Scene 3", isGroup = false, depth = 1),
			ExportableScene(id = 4, name = "Scene 4", isGroup = false, depth = 1),
			ExportableScene(id = 5, name = "Group 5", isGroup = true, depth = 1),
			ExportableScene(id = 6, name = "Scene 6", isGroup = false, depth = 2),
			ExportableScene(id = 7, name = "Scene 7", isGroup = false, depth = 0),
		)
		assertEquals(expected, comp.state.value.exportableScenes)
	}

	@Test
	fun `beginProjectExport resets a scene limit left over from a previous export`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		comp.updateExportOptions(ExportOptions(sceneIds = setOf(3)))
		comp.confirmExportDialog(comp.state.value.exportOptions)
		comp.endProjectExport()

		comp.beginProjectExport()
		assertNull(comp.state.value.exportOptions.sceneIds)
	}

	@Test
	fun `beginProjectExport replaces a format that is no longer registered`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		comp.updateExportOptions(ExportOptions(format = "removed.fmt"))
		comp.confirmExportDialog(comp.state.value.exportOptions)
		comp.endProjectExport()

		comp.beginProjectExport()
		assertEquals(BuiltInExportFormat.EPUB, comp.state.value.exportOptions.format)
	}

	@Test
	fun `A remembered plugin format falls back to the default when its plugin goes away`() = runTest(mainTestDispatcher) {
		pluginFormats.formats.value = listOf(FakeExporter("fake.fdx", "fdx"))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.rememberFormat("fake.fdx")
		assertEquals("fake.fdx", comp.state.value.exportOptions.format)

		pluginFormats.formats.value = emptyList()
		advanceUntilIdle()

		assertEquals(BuiltInExportFormat.EPUB, comp.state.value.exportOptions.format)
		assertEquals("Test.epub", comp.getExportStoryFileName(comp.state.value.exportOptions.format).fileName)
		assertEquals("Test.epub", comp.getExportStoryFileName("fake.fdx").fileName)
	}

	@Test
	fun `A remembered plugin format falls back to the default when the project turns its plugin off`() =
		runTest(mainTestDispatcher) {
			pluginFormats.formats.value = listOf(FakeExporter("fake.fdx", "fdx"))
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()
			comp.rememberFormat("fake.fdx")

			projectPlugins.setPluginEnabled(projectDef.id, "fake", enabled = false)
			advanceUntilIdle()

			assertEquals(BuiltInExportFormat.EPUB, comp.state.value.exportOptions.format)
			assertEquals("Test.epub", comp.getExportStoryFileName("fake.fdx").fileName)
		}

	@Test
	fun `Cancelling the dialog does not restore a format whose plugin went away`() = runTest(mainTestDispatcher) {
		pluginFormats.formats.value = listOf(FakeExporter("fake.fdx", "fdx"))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.rememberFormat("fake.fdx")

		comp.beginProjectExport()
		pluginFormats.formats.value = emptyList()
		advanceUntilIdle()
		comp.cancelExportDialog()
		comp.exportDialogDismissed()

		assertEquals(BuiltInExportFormat.EPUB, comp.state.value.exportOptions.format)
	}

	@Test
	fun `The export file name keeps a dotted extension whole`() = runTest(mainTestDispatcher) {
		pluginFormats.formats.value = listOf(FakeExporter("fake.tgz", "tar.gz"))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(ExportFileName("Test", "tar.gz"), comp.getExportStoryFileName("fake.tgz"))
	}

	@Test
	fun `updateExportOptions persists in-dialog edits in component state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		val edited = ExportOptions(
			treatTopLevelAsChapters = false,
			format = BuiltInExportFormat.PDF,
			sceneIds = setOf(3, 4),
		)
		comp.updateExportOptions(edited)

		// The dialog composition holds no option state, so surviving here is surviving rotation.
		assertEquals(edited, comp.state.value.exportOptions)
	}

	@Test
	fun `confirmExportDialog carries the scene filter through to the use case`() = runTest(mainTestDispatcher) {
		val options = ExportOptions(format = BuiltInExportFormat.MARKDOWN, sceneIds = setOf(3, 6))
		val exported = HPath("/out/Test.md", "Test.md", true)
		coEvery { exportStoryUseCase.execute(any(), any()) } returns exported

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.beginProjectExport()
		comp.confirmExportDialog(options)
		assertEquals(options, comp.state.value.exportOptions)

		comp.exportProject("/out", options)
		advanceUntilIdle()

		coVerify { exportStoryUseCase.execute(any(), match { it.sceneIds == setOf(3, 6) }) }
	}

	@Test
	fun `dismissal after cancel discards in-dialog edits and drops the scenes snapshot`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		val defaults = comp.state.value.exportOptions
		comp.beginProjectExport()
		advanceUntilIdle()
		assertTrue(comp.state.value.exportableScenes.isNotEmpty())

		comp.updateExportOptions(ExportOptions(treatTopLevelAsChapters = false, format = BuiltInExportFormat.PDF))
		comp.cancelExportDialog()
		// The snapshot survives until the close animation finishes, no mid-animation flash.
		assertTrue(comp.state.value.exportableScenes.isNotEmpty())

		comp.exportDialogDismissed()
		assertEquals(defaults.copy(sceneIds = null), comp.state.value.exportOptions)
		assertTrue(comp.state.value.exportableScenes.isEmpty())
	}

	@Test
	fun `dismissal after confirm keeps the confirmed options`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.beginProjectExport()
		val confirmed = ExportOptions(format = BuiltInExportFormat.RTF, sceneIds = setOf(3))
		comp.updateExportOptions(confirmed)
		comp.confirmExportDialog(confirmed)
		comp.endProjectExport()
		comp.exportDialogDismissed()

		assertEquals(confirmed, comp.state.value.exportOptions)
	}

	@Test
	fun `exportProject delegates to the use case and resets export state`() = runTest(mainTestDispatcher) {
		val options = ExportOptions(format = BuiltInExportFormat.MARKDOWN)
		val exported = HPath("/out/Test.md", "Test.md", true)
		coEvery { exportStoryUseCase.execute(any(), any()) } returns exported

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.beginProjectExport()
		comp.confirmExportDialog(options)

		val result = comp.exportProject("/out", options)
		advanceUntilIdle()

		assertEquals(exported, result)
		coVerify { exportStoryUseCase.execute(match { it.path == "/out" }, options) }
		val state = comp.state.value
		assertFalse(state.isExporting)
		assertFalse(state.showExportDialog)
		assertFalse(state.showExportFilePicker)
	}

	@Test
	fun `Failed export still clears the exporting state`() = runTest(mainTestDispatcher) {
		coEvery { exportStoryUseCase.executeToFile(any(), any()) } throws IllegalStateException("disk full")

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.beginProjectExport()

		assertFailsWith<IllegalStateException> {
			comp.exportProjectToFile("/out/Test.md", ExportOptions())
		}
		advanceUntilIdle()

		assertFalse(comp.state.value.isExporting)
		assertFalse(comp.state.value.showExportDialog)
	}

	@Test
	fun `Export file name is built from project name and format`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		assertEquals("Test.epub", comp.getExportStoryFileName(BuiltInExportFormat.EPUB).fileName)
		assertEquals("Test.docx", comp.getExportStoryFileName(BuiltInExportFormat.DOCX).fileName)
	}

	@Test
	fun `Successful backup invokes the callback and toasts success`() = runTest(mainTestDispatcher) {
		val backup = ProjectBackupDef(
			path = HPath("/backups/Test.zip", "Test.zip", true),
			projectName = projectDef.name,
			date = Instant.parse("2026-07-01T00:00:00Z"),
		)
		every { projectBackupService.supportsBackup() } returns true
		coEvery { projectBackupService.createBackup(projectDef) } returns backup

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val toasts = mutableListOf<ToastMessage>()
		val collectJob = launch { comp.toast.collect { toasts.add(it) } }
		advanceUntilIdle()

		var result: ProjectBackupDef? = null
		comp.createBackup { result = it }
		advanceUntilIdle()
		collectJob.cancel()

		assertTrue(comp.supportsBackup())
		assertEquals(backup, result)
		assertEquals(
			listOf<ToastMessage>(
				ToastMessage.Resource(Res.string.project_home_action_backup_toast_success, "Test.zip")
			),
			toasts,
		)
	}

	@Test
	fun `Failed backup passes null to the callback and toasts failure`() = runTest(mainTestDispatcher) {
		coEvery { projectBackupService.createBackup(projectDef) } returns null

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val toasts = mutableListOf<ToastMessage>()
		val collectJob = launch { comp.toast.collect { toasts.add(it) } }
		advanceUntilIdle()

		var callbackInvoked = false
		var result: ProjectBackupDef? = ProjectBackupDef(HPath("", "", true), projectDef.name, Instant.DISTANT_PAST)
		comp.createBackup { callbackInvoked = true; result = it }
		advanceUntilIdle()
		collectJob.cancel()

		assertTrue(callbackInvoked)
		assertNull(result)
		assertEquals(
			listOf<ToastMessage>(
				ToastMessage.Resource(Res.string.project_home_action_backup_toast_failure)
			),
			toasts,
		)
	}

	@Test
	fun `Scene shortcuts open scenes from stats`() = runTest(mainTestDispatcher) {
		val longest = SceneItem(projectDef, SceneItem.Type.Scene, 7, "The Long One", 0)
		val lastEdited = SceneItem(projectDef, SceneItem.Type.Scene, 3, "Fresh Ink", 1)
		every { sceneEditor.getSceneItemFromId(7) } returns longest
		every { sceneEditor.getSceneItemFromId(3) } returns lastEdited

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		statsFlow.emit(stats())
		advanceUntilIdle()

		comp.showLongestScene()
		assertEquals(longest, shownScene)

		comp.showLastEditedScene()
		assertEquals(lastEdited, shownScene)
	}

	@Test
	fun `Scene shortcuts do nothing before stats have loaded`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showLongestScene()
		comp.showLastEditedScene()

		assertNull(shownScene)
	}

	@Test
	fun `showEntry opens the encyclopedia entry when it exists`() = runTest(mainTestDispatcher) {
		val def = EntryDef(projectDef, 9, EntryType.PERSON, "Alice")
		every { encyclopediaService.findEntryDef(9) } returns def
		every { encyclopediaService.findEntryDef(99) } returns null

		val comp = newComponent()
		context.resume()

		comp.showEntry(EntryAppearance(entryId = 9, name = "Alice", type = EntryType.PERSON, sceneCount = 4))
		assertEquals(def, shownEntry)

		shownEntry = null
		comp.showEntry(EntryAppearance(entryId = 99, name = "Gone", type = EntryType.PLACE, sceneCount = 0))
		assertNull(shownEntry)
	}

	@Test
	fun `Successful sync reloads statistics but failed sync does not`() = runTest(mainTestDispatcher) {
		newComponent()
		context.resume()
		advanceUntilIdle()
		val callsBefore = loadStatisticsCalls

		syncCompleteChannel.send(true)
		advanceUntilIdle()
		assertEquals(callsBefore + 1, loadStatisticsCalls)

		syncCompleteChannel.send(false)
		advanceUntilIdle()
		assertEquals(callsBefore + 1, loadStatisticsCalls)
	}

	@Test
	fun `refreshStatistics shows the loading state until fresh stats arrive`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.refreshStatistics()
		advanceUntilIdle()

		assertTrue(comp.state.value.isLoadingStats)
		coVerify { statisticsService.recalculateStatistics(any()) }

		statsFlow.emit(stats())
		advanceUntilIdle()

		assertFalse(comp.state.value.isLoadingStats)
		assertEquals(1200, comp.state.value.totalWords)
	}

	@Test
	fun `Tag index emissions produce tag breakdowns`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		tagIndexFlow.value = TagIndex(
			tagToEntities = mapOf(
				"magic" to setOf(
					TaggedEntityRef(TaggedEntityType.Note, 1),
					TaggedEntityRef(TaggedEntityType.Scene, 2),
				)
			),
			countsByType = mapOf(
				TaggedEntityType.Note to mapOf("magic" to 1),
				TaggedEntityType.Scene to mapOf("magic" to 1),
			),
		)
		advanceUntilIdle()

		val state = comp.state.value
		val breakdown = state.tagBreakdowns.single()
		assertEquals("magic", breakdown.name)
		assertEquals(2, breakdown.total)
		assertEquals(
			mapOf(TaggedEntityType.Note to 1, TaggedEntityType.Scene to 1),
			state.tagUsesByType,
		)
	}

	@Test
	fun `Dirty and calculating flags flow into state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(comp.state.value.isStatsDirty)
		isDirtyFlow.value = true
		advanceUntilIdle()
		assertTrue(comp.state.value.isStatsDirty)

		isCalculatingFlow.value = true
		advanceUntilIdle()
		assertTrue(comp.state.value.isLoadingStats)

		isCalculatingFlow.value = false
		advanceUntilIdle()
		assertFalse(comp.state.value.isLoadingStats)
	}

	@Test
	fun `Settings navigation pushes and pops the content router`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<ProjectHome.ContentDestination.Stats>(comp.contentRouterState.value.active.instance)

		comp.showProjectSettings()
		advanceUntilIdle()
		assertIs<ProjectHome.ContentDestination.ProjectSettings>(comp.contentRouterState.value.active.instance)

		comp.onBack()
		advanceUntilIdle()
		assertIs<ProjectHome.ContentDestination.Stats>(comp.contentRouterState.value.active.instance)

		comp.showProjectSettings()
		advanceUntilIdle()
		comp.showProjectStats()
		advanceUntilIdle()
		assertIs<ProjectHome.ContentDestination.Stats>(comp.contentRouterState.value.active.instance)
	}

	@Test
	fun `Navigation callbacks are forwarded`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()

		comp.startProjectSync()
		assertTrue(syncShown)

		comp.showGlobalSearch()
		assertTrue(globalSearchShown)

		comp.showGlobalSearchForTag("magic")
		assertEquals("magic", searchedTag)

		comp.showImageGallery()
		assertTrue(imageGalleryShown)

		comp.closeProject()
		assertTrue(projectClosed)
	}

	private fun coverFile(id: Int, hash: String) = ImageFile(
		def = ImageDef(id = id, name = "Cover $id", fileExtension = "png"),
		path = HPath(path = "/images/$id~Cover $id.png", name = "$id~Cover $id.png", isAbsolute = true),
		contentHash = hash,
	)

	@Test
	fun `The cover follows the project data and its image`() = runTest(mainTestDispatcher) {
		imageFiles[4] = coverFile(4, "h1")
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		assertNull(comp.state.value.coverImage)

		projectDataState.value = StoredProjectData(ProjectData(coverImageId = 4))
		advanceUntilIdle()
		assertEquals(coverFile(4, "h1"), comp.state.value.coverImage)

		imageFiles[4] = coverFile(4, "h2")
		imageChangedFlow.emit(9)
		advanceUntilIdle()
		assertEquals("h1", comp.state.value.coverImage?.contentHash)
		imageChangedFlow.emit(4)
		advanceUntilIdle()
		assertEquals("h2", comp.state.value.coverImage?.contentHash)

		projectDataState.value = StoredProjectData(ProjectData())
		advanceUntilIdle()
		assertNull(comp.state.value.coverImage)
	}

	@Test
	fun `A cover whose image is not on this device shows nothing`() = runTest(mainTestDispatcher) {
		projectDataState.value = StoredProjectData(ProjectData(coverImageId = 7))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertNull(comp.state.value.coverImage)

		imageFiles[7] = coverFile(7, "h7")
		imageChangedFlow.emit(7)
		advanceUntilIdle()
		assertEquals(coverFile(7, "h7"), comp.state.value.coverImage)
	}
}
