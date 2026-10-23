package components.projectselection.projectslist

import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.group_error_in_use
import com.darkrockstudios.apps.hammer.group_error_source_missing
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.projects_list_toast_delete_failed
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsListComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountReauthUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ReauthResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.MarkdownStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.RtfStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporterRegistry
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckDatasource
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.test.get
import testGroupId
import testProjectId
import utils.ComponentTest
import utils.TestComponentContext
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectsListComponentTest : ComponentTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var synchronizer: ClientAccountSynchronizer
	private lateinit var networkConnectivity: NetworkConnectivity
	private lateinit var statsReader: ProjectStatisticsCacheReader
	private lateinit var reauthUseCase: AccountReauthUseCase

	private lateinit var globalSettings: GlobalSettings
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var serverSettingsUpdates: MutableSharedFlow<ServerSettings?>

	private var selectedProject: ProjectDef? = null

	private var previewsParsed = 0

	/** Counts parses so a test can tell one debounced parse from a burst of them. */
	private inner class CountingImporter(private val delegate: StoryImporter) : StoryImporter {
		override val format = delegate.format

		override fun preview(
			sourceName: String,
			content: ByteArray,
			options: ImportOptions,
		): ImportPreview {
			previewsParsed++
			return delegate.preview(sourceName, content, options)
		}
	}

	private val projectDefA = ProjectDef(testProjectId("Alpha"), "Alpha", HPath("/projects/Alpha", "Alpha", false))

	private val fixedNow = Instant.fromEpochSeconds(1_000_000)

	private val trilogy = GroupDef(
		id = testGroupId("Trilogy"),
		name = "Trilogy",
		path = HPath("/projects/Trilogy", "Trilogy", false),
		parentId = null,
		description = "Three books",
	)
	private val drafts = GroupDef(
		id = testGroupId("Drafts"),
		name = "Drafts",
		path = HPath("/projects/Trilogy/Drafts", "Drafts", false),
		parentId = trilogy.id,
		description = null,
	)
	private val bookOne =
		ProjectDef(testProjectId("Book One"), "Book One", HPath("/projects/Trilogy/Book One", "Book One", false))

	private val groupedTree = ProjectTree(
		groups = listOf(trilogy, drafts),
		projects = listOf(bookOne, projectDefA),
		placement = mapOf(bookOne.id to trilogy.id, projectDefA.id to null),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		globalSettingsStore = mockk(relaxed = true)
		projectsRepository = mockk(relaxed = true)
		synchronizer = mockk(relaxed = true)
		networkConnectivity = mockk(relaxed = true)
		statsReader = mockk(relaxed = true)
		reauthUseCase = mockk(relaxed = true)

		globalSettings = GlobalSettings(
			projectsDirectory = "/projects",
			spellCheckSettings = SpellCheckerSettings(locale = mockk()),
		)
		settingsUpdates = MutableSharedFlow()
		serverSettingsUpdates = MutableSharedFlow()

		every { globalSettingsStore.globalSettings } answers { globalSettings }
		every { globalSettingsStore.globalSettingsUpdates } returns settingsUpdates
		every { globalSettingsStore.serverSettingsUpdates } returns serverSettingsUpdates
		every { synchronizer.isServerSynchronized() } returns false
		// loadProjectList runs after most mutations; default to no projects (parallelMap no-ops on empty).
		every { projectsRepository.getProjectTree(any()) } returns ProjectTree.EMPTY
		every { projectsRepository.getProjects(any()) } returns emptyList()

		setupKoin(module {
			single { globalSettingsStore }
			single { projectsRepository }
			single { synchronizer }
			single { networkConnectivity }
			single { statsReader }
			single { reauthUseCase }
			factory { SyncAccountUseCase(get(), get(), get()) }
			single<FileSystem> { FakeFileSystem() }
			single<Toml> { createTomlSerializer() }
			single<StrRes> { TestStrRes() }
			single<Clock> { object : Clock { override fun now(): Instant = fixedNow } }
			single {
				StoryImporterRegistry(
					listOf(CountingImporter(MarkdownStoryImporter()), CountingImporter(RtfStoryImporter()))
				)
			}
			single<UrlLauncher> { mockk(relaxed = true) }
			single<VersionCheckDatasource> { mockk(relaxed = true) }
			single { VersionCheckRepository(get()) }
		})

		selectedProject = null
	}

	private fun newComponent() = ProjectsListComponent(
		componentContext = context,
		onProjectSelected = { selectedProject = it },
	)

	private fun flatTree(vararg projects: ProjectDef) =
		ProjectTree(groups = emptyList(), projects = projects.toList(), placement = projects.associate { it.id to null })

	private fun metadata(lastAccessed: Instant, projectId: ProjectId? = null) =
		ProjectMetadata(
			Info(
				created = Instant.fromEpochSeconds(1),
				lastAccessed = lastAccessed,
				projectId = projectId
			)
		)

	// --- initial state -------------------------------------------------------

	@Test
	fun `initial state reflects the projects directory and server-synced flag`() = runTest(mainTestDispatcher) {
		globalSettings = globalSettings.copy(projectsDirectory = "/somewhere/projects")
		every { synchronizer.isServerSynchronized() } returns true

		val comp = newComponent()

		assertEquals("/somewhere/projects", comp.state.value.projectsPath.path)
		assertTrue(comp.state.value.isServerSynced)
	}

	@Test
	fun `onProjectNameUpdate updates the create-dialog name`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.onProjectNameUpdate("My Novel")

		assertEquals("My Novel", comp.state.value.createDialogProjectName)
	}

	// --- createProject -------------------------------------------------------

	@Test
	fun `createProject success creates locally and clears the dialog without queueing a server create`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			every { projectsRepository.createProject("Novel", any(), any()) } returns CResult.success(projectDefA)
			val comp = newComponent()
			comp.onProjectNameUpdate("Novel")

			comp.createProject("Novel")
			advanceUntilIdle()

			verify { projectsRepository.createProject("Novel", any(), any()) }
			// Account sync uploads every local id the server does not know; nothing is queued.
			coVerify(exactly = 0) { synchronizer.deleteProject(any()) }
			assertEquals("", comp.state.value.createDialogProjectName)
		}

	@Test
	fun `createProject failure leaves the dialog open and does not reload or sync`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.createProject("bad/name", any(), any()) } returns
				CResult.failure(error = "invalid", displayMessage = "Bad name".toMsg())
			val comp = newComponent()
			comp.onProjectNameUpdate("bad/name")

			comp.createProject("bad/name")
			advanceUntilIdle()

			verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
			assertEquals("bad/name", comp.state.value.createDialogProjectName)
		}

	// --- import --------------------------------------------------------------

	@Test
	fun `beginProjectImport dismisses the create modal and opens the file picker`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.showCreate()

			comp.beginProjectImport()

			assertTrue(comp.state.value.showImportFilePicker)
			assertEquals("", comp.state.value.createDialogProjectName)
			assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
		}

	@Test
	fun `selectImportFile derives the project name from the file name and shows the import dialog`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()

			comp.selectImportFile("The Wreck.md", "# Chapter One\n\nText\n\n# Chapter Two\n\nMore".encodeToByteArray())
			advanceUntilIdle()

			assertEquals("The Wreck", comp.state.value.importProjectName)
			assertTrue(comp.state.value.showImportDialog)
			assertFalse(comp.state.value.showImportFilePicker)
			assertFalse(comp.state.value.importPreview.isEmpty)
		}

	@Test
	fun `selectImportFile prefills the project name with a detected title`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()

			comp.selectImportFile(
				"alice.md",
				"# Alice in Wonderland\n\n## Chapter One\n\nText".encodeToByteArray(),
			)
			advanceUntilIdle()

			assertEquals("Alice in Wonderland", comp.state.value.importProjectName)
		}

	// Auto keeps two H2 scenes; H1 collapses the document to one, so the preview shows which won.
	private val twoLevelDoc =
		"# Part One\n\n## Chapter A\n\nText\n\n## Chapter B\n\nMore".encodeToByteArray()

	@Test
	fun `the dialog opens busy and clears when the parse lands`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()

			comp.selectImportFile("The Wreck.md", twoLevelDoc)

			// The picker has closed, so the dialog carries the wait rather than the projects list.
			assertTrue(comp.state.value.showImportDialog, "Dialog opens before the parse finishes")
			assertTrue(comp.state.value.isParsingImport, "It opens in the busy state")
			assertTrue(comp.state.value.importPreview.isEmpty, "With nothing to show yet")

			advanceUntilIdle()

			assertFalse(comp.state.value.isParsingImport, "Busy clears once the preview lands")
			assertFalse(comp.state.value.importPreview.isEmpty)
		}

	@Test
	fun `a burst of option changes costs one parse and settles on the last`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.selectImportFile("Draft.md", twoLevelDoc)
			advanceUntilIdle()
			assertEquals(2, comp.state.value.importPreview.totalScenes, "Auto splits on H2")
			previewsParsed = 0

			comp.updateImportOptions(ImportOptions(markdownSplitStrategy = MarkdownSplitStrategy.H2))
			comp.updateImportOptions(ImportOptions(markdownSplitStrategy = MarkdownSplitStrategy.Auto))
			comp.updateImportOptions(ImportOptions(markdownSplitStrategy = MarkdownSplitStrategy.H1))
			advanceUntilIdle()

			assertEquals(1, previewsParsed, "Superseded options must be dropped before they parse")
			assertEquals(MarkdownSplitStrategy.H1, comp.state.value.importOptions.markdownSplitStrategy)
			assertEquals(1, comp.state.value.importPreview.totalScenes, "H1 keeps it as one scene")
		}

	@Test
	fun `confirming an empty preview creates nothing`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			comp.selectImportFile("Blank.md", "   \n\n  \n".encodeToByteArray())
			advanceUntilIdle()
			assertTrue(comp.state.value.importPreview.isEmpty, "A blank document previews as nothing")

			comp.confirmImportDialog()
			advanceUntilIdle()

			verify(exactly = 0) { projectsRepository.createProject(any(), any()) }
		}

	@Test
	fun `updateImportProjectName updates the import name`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.updateImportProjectName("Renamed")

		assertEquals("Renamed", comp.state.value.importProjectName)
	}

	@Test
	fun `cancelImportDialog clears the import state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		comp.selectImportFile("Draft.md", "# One\n\nText".encodeToByteArray())
		advanceUntilIdle()
		assertTrue(comp.state.value.showImportDialog)

		comp.cancelImportDialog()

		assertFalse(comp.state.value.showImportDialog)
		assertEquals("", comp.state.value.importProjectName)
		assertTrue(comp.state.value.importPreview.isEmpty)
	}

	@Test
	fun `confirmImportDialog leaves the dialog open and does not sync when project creation fails`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			every { projectsRepository.createProject("Draft", any(), any()) } returns
				CResult.failure(error = "exists", displayMessage = "Project already exists".toMsg())
			val comp = newComponent()
			comp.selectImportFile("Draft.md", "# One\n\nText\n\n# Two\n\nMore".encodeToByteArray())

			comp.confirmImportDialog()
			advanceUntilIdle()

			assertTrue(comp.state.value.showImportDialog)
			verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
		}

	// --- deleteProject -------------------------------------------------------

	@Test
	fun `deleteProject deletes locally and queues the server delete by id`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.deleteProject(projectDefA) } returns true
			val comp = newComponent()

			comp.deleteProject(projectDefA)
			advanceUntilIdle()

			coVerify { synchronizer.deleteProject(projectDefA) }
		}

	@Test
	fun `deleteProject does not touch the filesystem on the calling thread`() = runTest(mainTestDispatcher) {
		every { projectsRepository.deleteProject(projectDefA) } returns true
		val comp = newComponent()

		comp.deleteProject(projectDefA)

		verify(exactly = 0) { projectsRepository.deleteProject(any()) }

		advanceUntilIdle()

		verify { projectsRepository.deleteProject(projectDefA) }
	}

	@Test
	fun `deleteProject queues the server delete even with no server configured`() = runTest(mainTestDispatcher) {
		every { synchronizer.isServerSynchronized() } returns false
		every { projectsRepository.deleteProject(projectDefA) } returns true
		val comp = newComponent()

		comp.deleteProject(projectDefA)
		advanceUntilIdle()

		coVerify { synchronizer.deleteProject(projectDefA) }
	}

	@Test
	fun `deleteProject does nothing when the local delete fails`() = runTest(mainTestDispatcher) {
		every { projectsRepository.deleteProject(projectDefA) } returns false
		val comp = newComponent()
		val toast = async(start = CoroutineStart.UNDISPATCHED) { comp.toast.first() }

		comp.deleteProject(projectDefA)
		advanceUntilIdle()

		coVerify(exactly = 0) { synchronizer.deleteProject(any()) }
		verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
		assertEquals(ToastMessage.Resource(Res.string.projects_list_toast_delete_failed), toast.await())
	}

	@Test
	fun `deleteProject shows a failure when the filesystem throws`() = runTest(mainTestDispatcher) {
		every { projectsRepository.deleteProject(projectDefA) } throws IOException("Access denied")
		val comp = newComponent()
		val toast = async(start = CoroutineStart.UNDISPATCHED) { comp.toast.first() }

		comp.deleteProject(projectDefA)
		advanceUntilIdle()

		coVerify(exactly = 0) { synchronizer.deleteProject(any()) }
		verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
		assertEquals(ToastMessage.Resource(Res.string.projects_list_toast_delete_failed), toast.await())
	}

	// --- renameProject -------------------------------------------------------

	@Test
	fun `renameProject renames locally and reloads without queueing anything`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.renameProject(projectDefA, "Beta") } returns CResult.success(projectDefA)
			val comp = newComponent()

			comp.renameProject(projectDefA, "Beta")
			advanceUntilIdle()

			// Account sync finds the rename on disk through its baseline.
			verify { projectsRepository.getProjectTree(any()) }
			coVerify(exactly = 0) { synchronizer.deleteProject(any()) }
		}

	@Test
	fun `renameProject failure does not reload`() = runTest(mainTestDispatcher) {
		every { projectsRepository.renameProject(projectDefA, "Beta") } returns
			CResult.failure(error = "nope")
		val comp = newComponent()

		comp.renameProject(projectDefA, "Beta")
		advanceUntilIdle()

		verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
	}

	// --- selectProject / dialog ----------------------------------------------

	@Test
	fun `selectProject bumps last-accessed and notifies the parent`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.selectProject(projectDefA)

		val transform = slot<(ProjectMetadata) -> ProjectMetadata>()
		verify { projectsRepository.updateProjectMetadata(eq(projectDefA), capture(transform)) }
		val before = metadata(lastAccessed = Instant.fromEpochSeconds(5))
		assertEquals(
			before.copy(info = before.info.copy(lastAccessed = fixedNow)),
			transform.captured(before),
		)
		assertEquals(projectDefA, selectedProject)
	}

	@Test
	fun `hideCreate clears the create-dialog name`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		comp.onProjectNameUpdate("Half typed")

		comp.hideCreate()

		assertEquals("", comp.state.value.createDialogProjectName)
	}

	// --- modal routing -------------------------------------------------------

	@Test
	fun `showCreate routes to the create modal and hideCreate dismisses it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.showCreate()
		assertIs<ProjectsList.ModalDestination.ProjectCreate>(comp.modalRouterState.value.child?.instance)

		comp.hideCreate()
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `showProjectRename routes to the rename modal carrying the project`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.showProjectRename(projectDefA)

		val dest = comp.modalRouterState.value.child?.instance
		assertIs<ProjectsList.ModalDestination.ProjectRename>(dest)
		assertEquals(projectDefA, dest.projectDef)

		comp.dismissProjectRename()
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `showProjectDelete routes to the delete modal carrying the project`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.showProjectDelete(projectDefA)

		val dest = comp.modalRouterState.value.child?.instance
		assertIs<ProjectsList.ModalDestination.ProjectDelete>(dest)
		assertEquals(projectDefA, dest.projectDef)

		comp.dismissProjectDelete()
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	// --- sync reauthentication -------------------------------------------------

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `successful reauthentication after an unauthorized sync starts a new sync`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			var syncCalls = 0
			coEvery { synchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
				syncCalls++
				// First sync hits a 401 and reports unauthorized; the retry just fails plainly.
				if (syncCalls == 1) secondArg<suspend () -> Unit>().invoke()
				false
			}
			coEvery { reauthUseCase.reauthenticate(any()) } returns ReauthResult.Success
			val comp = newComponent()

			comp.showProjectsSync()
			advanceUntilIdle()

			val reauth = comp.modalRouterState.value.child?.instance
			assertIs<ProjectsList.ModalDestination.ServerReauth>(reauth)

			reauth.component.updateServerPassword("hunter2")
			reauth.component.reauthenticate("hunter2")
			advanceUntilIdle()

			coVerify(exactly = 2) { synchronizer.syncProjects(any(), any(), any(), any()) }
			assertIs<ProjectsList.ModalDestination.ProjectSync>(comp.modalRouterState.value.child?.instance)
		}

	@Test
	fun `a project the server reports unchanged shows as complete in the sync dialog`() =
		runTest(mainTestDispatcher) {
			globalSettings = globalSettings.copy(autoCloseSyncDialog = false)
			every { synchronizer.isServerSynchronized() } returns true
			every { projectsRepository.getProjects(any()) } returns listOf(projectDefA)
			every { projectsRepository.getProjectTree(any()) } returns flatTree(projectDefA)
			every { projectsRepository.loadProjectMetadata(projectDefA) } returns
				metadata(Instant.fromEpochSeconds(1), projectId = projectDefA.id)
			coEvery { synchronizer.syncProjects(any(), any(), any(), any()) } returns true
			coEvery { synchronizer.probeUnchangedProjects(any()) } returns setOf(projectDefA.id)
			val comp = newComponent()

			comp.showProjectsSync()
			advanceUntilIdle()

			val status = comp.state.value.syncState.projectsStatus.getValue("Alpha")
			assertEquals(ProjectsList.Status.Complete, status.status)
			assertEquals(1f, status.progress)
			assertTrue(comp.state.value.syncState.syncComplete)
		}

	@Test
	fun `cancelling a sync relists the projects so their ids match what the sync changed`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			val adopted = projectDefA.copy(id = testProjectId("Adopted"))
			var onDisk = listOf(projectDefA)
			every { projectsRepository.getProjects(any()) } answers { onDisk }
			every { projectsRepository.getProjectTree(any()) } answers { flatTree(*onDisk.toTypedArray()) }
			every { projectsRepository.loadProjectMetadata(any()) } returns metadata(Instant.fromEpochSeconds(1))
			every { statsReader.loadTotalWords(any()) } returns null
			coEvery { synchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
				onDisk = listOf(adopted)
				awaitCancellation()
			}
			val comp = newComponent()
			comp.loadProjectList()
			comp.showProjectsSync()
			advanceUntilIdle()

			comp.cancelProjectsSync()
			advanceUntilIdle()

			assertEquals(listOf(adopted.id), comp.state.value.projects.map { it.definition.id })
		}

	// --- loadProjectList -----------------------------------------------------

	@Test
	fun `loadProjectList maps metadata to project data sorted by last-accessed descending`() =
		runTest(mainTestDispatcher) {
			val oldest = ProjectDef(testProjectId("Oldest"), "Oldest", HPath("/projects/Oldest", "Oldest", false))
			val newest = ProjectDef(testProjectId("Newest"), "Newest", HPath("/projects/Newest", "Newest", false))
			val middle = ProjectDef(testProjectId("Middle"), "Middle", HPath("/projects/Middle", "Middle", false))
			every { projectsRepository.getProjectTree(any()) } returns flatTree(oldest, newest, middle)
			every { projectsRepository.loadProjectMetadata(oldest) } returns metadata(Instant.fromEpochSeconds(100))
			every { projectsRepository.loadProjectMetadata(newest) } returns metadata(Instant.fromEpochSeconds(300))
			every { projectsRepository.loadProjectMetadata(middle) } returns metadata(Instant.fromEpochSeconds(200))
			every { statsReader.loadTotalWords(any()) } returns null
			val comp = newComponent()

			comp.loadProjectList()
			advanceUntilIdle()

			assertEquals(
				listOf("Newest", "Middle", "Oldest"),
				comp.state.value.projects.map { it.definition.name },
			)
		}

	@Test
	fun `a protocol mismatch shows the update dialog`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		// Start the lifecycle so onCreate() subscribes the mismatch observer.
		context.resume()
		advanceUntilIdle()

		get<ProtocolMismatchRepository>().notifyMismatch(
			clientProtocolVersion = 3,
			serverProtocolVersion = 5,
		)
		advanceUntilIdle()

		assertIs<ProjectsList.ModalDestination.ProtocolMismatch>(
			comp.modalRouterState.value.child?.instance,
		)
	}

	@Test
	fun `loadProjectList skips a project whose metadata fails to load`() =
		runTest(mainTestDispatcher) {
			val good = ProjectDef(testProjectId("Good"), "Good", HPath("/projects/Good", "Good", false))
			val vanished = ProjectDef(testProjectId("Vanished"), "Vanished", HPath("/projects/Vanished", "Vanished", false))
			every { projectsRepository.getProjectTree(any()) } returns flatTree(good, vanished)
			every { projectsRepository.loadProjectMetadata(good) } returns metadata(Instant.fromEpochSeconds(100))
			// Deleted concurrently between listing and reading: loadMetadata throws.
			every { projectsRepository.loadProjectMetadata(vanished) } throws IOException("deleted")
			every { statsReader.loadTotalWords(any()) } returns null
			val comp = newComponent()

			comp.loadProjectList()
			advanceUntilIdle()

			assertEquals(
				listOf("Good"),
				comp.state.value.projects.map { it.definition.name },
			)
		}

	// --- groups --------------------------------------------------------------

	private fun stubGroupedTree() {
		every { projectsRepository.getProjectTree(any()) } returns groupedTree.copy(collapsed = setOf(drafts.id))
		every { projectsRepository.loadProjectMetadata(any()) } returns metadata(Instant.fromEpochSeconds(1))
		every { statsReader.loadTotalWords(any()) } returns null
	}

	@Test
	fun `loadProjectList loads groups with their collapsed state and the project placement`() =
		runTest(mainTestDispatcher) {
			stubGroupedTree()
			val comp = newComponent()

			comp.loadProjectList()
			advanceUntilIdle()

			assertEquals(
				listOf(GroupData(trilogy, collapsed = false), GroupData(drafts, collapsed = true)),
				comp.state.value.groups,
			)
			assertEquals(groupedTree.placement, comp.state.value.placement)
			assertEquals(setOf("Book One", "Alpha"), comp.state.value.projects.map { it.definition.name }.toSet())
		}

	@Test
	fun `createProject in a group creates it there`() = runTest(mainTestDispatcher) {
		every { projectsRepository.createProject("Book Two", any(), any(), trilogy) } returns
			CResult.success(projectDefA)
		val comp = newComponent()
		comp.showCreate(trilogy)

		comp.createProject("Book Two", trilogy)
		advanceUntilIdle()

		verify { projectsRepository.createProject("Book Two", true, any(), trilogy) }
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `showCreate with a parent routes to the create modal carrying it`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.showCreate(trilogy)

		val dest = comp.modalRouterState.value.child?.instance
		assertIs<ProjectsList.ModalDestination.ProjectCreate>(dest)
		assertEquals(trilogy, dest.parent)
	}

	@Test
	fun `createGroup creates the group, closes the dialog and reloads`() = runTest(mainTestDispatcher) {
		every { projectsRepository.createGroup("Drafts", trilogy, any(), "Early work") } returns
			CResult.success(drafts)
		val comp = newComponent()
		comp.showGroupCreate(trilogy)

		comp.createGroup("Drafts", trilogy, "Early work")
		advanceUntilIdle()

		verify { projectsRepository.createGroup("Drafts", trilogy, any(), "Early work") }
		verify { projectsRepository.getProjectTree(any()) }
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `a failed group action toasts the reason and leaves the dialog open without reloading`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.renameGroup(trilogy, "Saga") } returns
				CResult.failure(error = "GroupInUse", displayMessage = Res.string.group_error_in_use.toMsg())
			val comp = newComponent()
			comp.showGroupRename(trilogy)
			val toast = async(start = CoroutineStart.UNDISPATCHED) { comp.toast.first() }

			comp.renameGroup(trilogy, "Saga")
			advanceUntilIdle()

			assertEquals(ToastMessage.Resource(Res.string.group_error_in_use), toast.await())
			verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
			assertIs<ProjectsList.ModalDestination.GroupRename>(comp.modalRouterState.value.child?.instance)
		}

	@Test
	fun `moveProject moves the project off the calling thread and reloads`() = runTest(mainTestDispatcher) {
		every { projectsRepository.moveProject(projectDefA, trilogy) } returns CResult.success(projectDefA)
		val comp = newComponent()
		comp.showMoveToGroup(MoveTarget.Project(projectDefA))

		comp.moveProject(projectDefA, trilogy)
		verify(exactly = 0) { projectsRepository.moveProject(any(), any()) }
		advanceUntilIdle()

		verify { projectsRepository.moveProject(projectDefA, trilogy) }
		verify { projectsRepository.getProjectTree(any()) }
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `moveGroup to the top level moves it there and reloads`() = runTest(mainTestDispatcher) {
		every { projectsRepository.moveGroup(drafts, null) } returns CResult.success(drafts.copy(parentId = null))
		val comp = newComponent()

		comp.moveGroup(drafts, null)
		advanceUntilIdle()

		verify { projectsRepository.moveGroup(drafts, null) }
		verify { projectsRepository.getProjectTree(any()) }
	}

	@Test
	fun `deleteGroup deletes it and reloads`() = runTest(mainTestDispatcher) {
		every { projectsRepository.deleteGroup(trilogy) } returns CResult.success()
		val comp = newComponent()
		comp.showGroupDelete(trilogy)

		comp.deleteGroup(trilogy)
		advanceUntilIdle()

		verify { projectsRepository.deleteGroup(trilogy) }
		verify { projectsRepository.getProjectTree(any()) }
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `a failed deleteGroup closes the confirmation and toasts the reason`() = runTest(mainTestDispatcher) {
		every { projectsRepository.deleteGroup(trilogy) } returns
			CResult.failure(error = "GroupInUse", displayMessage = Res.string.group_error_in_use.toMsg())
		val comp = newComponent()
		comp.showGroupDelete(trilogy)
		val toast = async(start = CoroutineStart.UNDISPATCHED) { comp.toast.first() }

		comp.deleteGroup(trilogy)
		advanceUntilIdle()

		assertEquals(ToastMessage.Resource(Res.string.group_error_in_use), toast.await())
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `updateGroupDescription on a vanished group toasts that it is gone`() = runTest(mainTestDispatcher) {
		every { projectsRepository.updateGroupDescription(trilogy, "New") } returns
			CResult.failure(error = "SourceDoesNotExist", displayMessage = Res.string.group_error_source_missing.toMsg())
		val comp = newComponent()
		val toast = async(start = CoroutineStart.UNDISPATCHED) { comp.toast.first() }

		comp.updateGroupDescription(trilogy, "New")
		advanceUntilIdle()

		assertEquals(ToastMessage.Resource(Res.string.group_error_source_missing), toast.await())
		verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
	}

	@Test
	fun `toggleGroupCollapsed flips the group in place and persists it without reloading`() =
		runTest(mainTestDispatcher) {
			stubGroupedTree()
			val comp = newComponent()
			comp.loadProjectList()
			advanceUntilIdle()
			clearMocks(projectsRepository, answers = false)

			comp.toggleGroupCollapsed(trilogy)
			advanceUntilIdle()

			assertTrue(comp.state.value.groups.single { it.definition.id == trilogy.id }.collapsed)
			assertTrue(comp.state.value.groups.single { it.definition.id == drafts.id }.collapsed)
			verify { projectsRepository.setGroupCollapsed(trilogy, true) }
			verify(exactly = 0) { projectsRepository.getProjectTree(any()) }
		}

	@Test
	fun `group modals route with their group and dismiss back to none`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.showGroupDescription(drafts)
		assertEquals(drafts, assertIs<ProjectsList.ModalDestination.GroupDescription>(
			comp.modalRouterState.value.child?.instance
		).groupDef)

		comp.showMoveToGroup(MoveTarget.Group(drafts))
		assertEquals(MoveTarget.Group(drafts), assertIs<ProjectsList.ModalDestination.MoveToGroup>(
			comp.modalRouterState.value.child?.instance
		).target)

		comp.showGroupCreate(null)
		assertNull(assertIs<ProjectsList.ModalDestination.GroupCreate>(comp.modalRouterState.value.child?.instance).parent)

		comp.dismissGroupModal()
		assertIs<ProjectsList.ModalDestination.None>(comp.modalRouterState.value.child?.instance)
	}

	@Test
	fun `groups, placement and an open move dialog survive process death`() = runTest(mainTestDispatcher) {
		stubGroupedTree()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showMoveToGroup(MoveTarget.Project(bookOne))

		// Through the real encoding, as on Android, not the in-memory container.
		val json = Json.encodeToString(SerializableContainer.serializer(), context.stateKeeper.save())
		val restoredContext = TestComponentContext.create(Json.decodeFromString(SerializableContainer.serializer(), json))
		try {
			val restored = ProjectsListComponent(componentContext = restoredContext, onProjectSelected = {})

			assertEquals(comp.state.value.groups, restored.state.value.groups)
			assertEquals(groupedTree.placement, restored.state.value.placement)
			assertEquals(
				MoveTarget.Project(bookOne),
				assertIs<ProjectsList.ModalDestination.MoveToGroup>(restored.modalRouterState.value.child?.instance).target,
			)
		} finally {
			restoredContext.destroy()
		}
	}
}
