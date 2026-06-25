package components.projectselection.projectslist

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsListComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.importer.MarkdownStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.RtfStoryImporter
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporterRegistry
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectsListComponentTest : ComponentTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var synchronizer: ClientAccountSynchronizer
	private lateinit var networkConnectivity: NetworkConnectivity
	private lateinit var metadataDatasource: ProjectMetadataDatasource
	private lateinit var statsReader: ProjectStatisticsCacheReader

	private lateinit var globalSettings: GlobalSettings
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var serverSettingsUpdates: MutableSharedFlow<ServerSettings?>

	private var selectedProject: ProjectDef? = null

	private val projectDefA = ProjectDef("Alpha", HPath("/projects/Alpha", "Alpha", false))

	@BeforeEach
	override fun setup() {
		super.setup()

		globalSettingsStore = mockk(relaxed = true)
		projectsRepository = mockk(relaxed = true)
		synchronizer = mockk(relaxed = true)
		networkConnectivity = mockk(relaxed = true)
		metadataDatasource = mockk(relaxed = true)
		statsReader = mockk(relaxed = true)

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
		every { projectsRepository.getProjects(any()) } returns emptyList()

		setupKoin(module {
			single { globalSettingsStore }
			single { projectsRepository }
			single { synchronizer }
			single { networkConnectivity }
			single { metadataDatasource }
			single { statsReader }
			single<FileSystem> { FakeFileSystem() }
			single<Toml> { createTomlSerializer() }
			single<StrRes> { TestStrRes() }
			single<Clock> { Clock.System }
			single { StoryImporterRegistry(listOf(MarkdownStoryImporter(), RtfStoryImporter())) }
		})

		selectedProject = null
	}

	private fun newComponent() = ProjectsListComponent(
		componentContext = context,
		onProjectSelected = { selectedProject = it },
	)

	private fun metadata(lastAccessed: Instant, serverId: ProjectId? = null) =
		ProjectMetadata(
			Info(
				created = Instant.fromEpochSeconds(1),
				lastAccessed = lastAccessed,
				serverProjectId = serverId
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
	fun `createProject success on a synced account creates locally and on the server then clears the dialog`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			every { projectsRepository.createProject("Novel") } returns CResult.success(projectDefA)
			val comp = newComponent()
			comp.onProjectNameUpdate("Novel")

			comp.createProject("Novel")
			advanceUntilIdle()

			verify { projectsRepository.createProject("Novel") }
			verify { synchronizer.createProject("Novel") }
			assertEquals("", comp.state.value.createDialogProjectName)
		}

	@Test
	fun `createProject success on a non-synced account does not touch the synchronizer`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns false
			every { projectsRepository.createProject("Novel") } returns CResult.success(projectDefA)
			val comp = newComponent()

			comp.createProject("Novel")
			advanceUntilIdle()

			verify(exactly = 0) { synchronizer.createProject(any()) }
		}

	@Test
	fun `createProject failure leaves the dialog open and does not reload or sync`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.createProject("bad/name") } returns
				CResult.failure(error = "invalid", displayMessage = "Bad name".toMsg())
			val comp = newComponent()
			comp.onProjectNameUpdate("bad/name")

			comp.createProject("bad/name")
			advanceUntilIdle()

			verify(exactly = 0) { synchronizer.createProject(any()) }
			verify(exactly = 0) { projectsRepository.getProjects(any()) }
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

			assertEquals("Alice in Wonderland", comp.state.value.importProjectName)
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

		comp.cancelImportDialog()

		assertFalse(comp.state.value.showImportDialog)
		assertEquals("", comp.state.value.importProjectName)
		assertTrue(comp.state.value.importPreview.isEmpty)
	}

	@Test
	fun `confirmImportDialog leaves the dialog open and does not sync when project creation fails`() =
		runTest(mainTestDispatcher) {
			every { synchronizer.isServerSynchronized() } returns true
			every { projectsRepository.createProject("Draft") } returns
				CResult.failure(error = "exists", displayMessage = "Project already exists".toMsg())
			val comp = newComponent()
			comp.selectImportFile("Draft.md", "# One\n\nText\n\n# Two\n\nMore".encodeToByteArray())

			comp.confirmImportDialog()
			advanceUntilIdle()

			assertTrue(comp.state.value.showImportDialog)
			verify(exactly = 0) { synchronizer.createProject(any()) }
			verify(exactly = 0) { projectsRepository.getProjects(any()) }
		}

	// --- deleteProject -------------------------------------------------------

	@Test
	fun `deleteProject deletes locally and on the server when the project has an id`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.getProjectId(projectDefA) } returns ProjectId("server-id")
			every { projectsRepository.deleteProject(projectDefA) } returns true
			val comp = newComponent()

			comp.deleteProject(projectDefA)
			advanceUntilIdle()

			verify { synchronizer.deleteProject(SyncedProjectDefinition(projectDefA, ProjectId("server-id"))) }
		}

	@Test
	fun `deleteProject skips the server when the project has no id`() = runTest(mainTestDispatcher) {
		every { projectsRepository.getProjectId(projectDefA) } returns null
		every { projectsRepository.deleteProject(projectDefA) } returns true
		val comp = newComponent()

		comp.deleteProject(projectDefA)
		advanceUntilIdle()

		verify(exactly = 0) { synchronizer.deleteProject(any()) }
	}

	@Test
	fun `deleteProject does nothing when the local delete fails`() = runTest(mainTestDispatcher) {
		every { projectsRepository.getProjectId(projectDefA) } returns ProjectId("server-id")
		every { projectsRepository.deleteProject(projectDefA) } returns false
		val comp = newComponent()

		comp.deleteProject(projectDefA)
		advanceUntilIdle()

		verify(exactly = 0) { synchronizer.deleteProject(any()) }
		verify(exactly = 0) { projectsRepository.getProjects(any()) }
	}

	// --- renameProject -------------------------------------------------------

	@Test
	fun `renameProject renames locally and on the server when the project has an id`() =
		runTest(mainTestDispatcher) {
			every { projectsRepository.getProjectId(projectDefA) } returns ProjectId("server-id")
			every { projectsRepository.renameProject(projectDefA, "Beta") } returns CResult.success(projectDefA)
			val comp = newComponent()

			comp.renameProject(projectDefA, "Beta")
			advanceUntilIdle()

			verify { synchronizer.renameProject(ProjectId("server-id"), "Beta") }
		}

	@Test
	fun `renameProject skips the server when the project has no id`() = runTest(mainTestDispatcher) {
		every { projectsRepository.getProjectId(projectDefA) } returns null
		every { projectsRepository.renameProject(projectDefA, "Beta") } returns CResult.success(projectDefA)
		val comp = newComponent()

		comp.renameProject(projectDefA, "Beta")
		advanceUntilIdle()

		verify(exactly = 0) { synchronizer.renameProject(any(), any()) }
	}

	@Test
	fun `renameProject failure does not sync or reload`() = runTest(mainTestDispatcher) {
		every { projectsRepository.getProjectId(projectDefA) } returns ProjectId("server-id")
		every { projectsRepository.renameProject(projectDefA, "Beta") } returns
			CResult.failure(error = "nope")
		val comp = newComponent()

		comp.renameProject(projectDefA, "Beta")
		advanceUntilIdle()

		verify(exactly = 0) { synchronizer.renameProject(any(), any()) }
		verify(exactly = 0) { projectsRepository.getProjects(any()) }
	}

	// --- selectProject / dialog ----------------------------------------------

	@Test
	fun `selectProject bumps last-accessed and notifies the parent`() = runTest(mainTestDispatcher) {
		val comp = newComponent()

		comp.selectProject(projectDefA)

		verify { metadataDatasource.updateMetadata(eq(projectDefA), any()) }
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

	// --- loadProjectList -----------------------------------------------------

	@Test
	fun `loadProjectList maps metadata to project data sorted by last-accessed descending`() =
		runTest(mainTestDispatcher) {
			val oldest = ProjectDef("Oldest", HPath("/projects/Oldest", "Oldest", false))
			val newest = ProjectDef("Newest", HPath("/projects/Newest", "Newest", false))
			val middle = ProjectDef("Middle", HPath("/projects/Middle", "Middle", false))
			every { projectsRepository.getProjects(any()) } returns listOf(oldest, newest, middle)
			every { metadataDatasource.loadMetadata(oldest) } returns metadata(Instant.fromEpochSeconds(100))
			every { metadataDatasource.loadMetadata(newest) } returns metadata(Instant.fromEpochSeconds(300))
			every { metadataDatasource.loadMetadata(middle) } returns metadata(Instant.fromEpochSeconds(200))
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
	fun `loadProjectList skips a project whose metadata fails to load`() =
		runTest(mainTestDispatcher) {
			val good = ProjectDef("Good", HPath("/projects/Good", "Good", false))
			val vanished = ProjectDef("Vanished", HPath("/projects/Vanished", "Vanished", false))
			every { projectsRepository.getProjects(any()) } returns listOf(good, vanished)
			every { metadataDatasource.loadMetadata(good) } returns metadata(Instant.fromEpochSeconds(100))
			// Deleted concurrently between listing and reading: loadMetadata throws.
			every { metadataDatasource.loadMetadata(vanished) } throws IOException("deleted")
			every { statsReader.loadTotalWords(any()) } returns null
			val comp = newComponent()

			comp.loadProjectList()
			advanceUntilIdle()

			assertEquals(
				listOf("Good"),
				comp.state.value.projects.map { it.definition.name },
			)
		}
}
