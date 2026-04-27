package integration

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import createProject
import getProjectsDirectory
import io.mockk.*
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import utils.BaseTest

/**
 * Base class for integration tests that use FakeFileSystem and real implementations.
 *
 * This follows the Classical School of testing - we use real collaborators where possible
 * and only mock external dependencies (like server API, statistics tracking).
 *
 * The goal is to test the interaction between multiple layers of the stack,
 * catching bugs that unit tests with extensive mocking would miss.
 */
abstract class BaseIntegrationTest : BaseTest() {

	protected lateinit var ffs: FakeFileSystem
	protected lateinit var toml: Toml

	// Real implementations
	protected lateinit var sceneDatasource: SceneDatasource
	protected lateinit var sceneEditorRepository: SceneEditorRepository
	protected lateinit var sceneMetadataDatasource: SceneMetadataDatasource
	protected lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	// Mocked external dependencies (things we don't want to test here)
	protected lateinit var syncDataRepository: SyncDataRepository
	protected lateinit var idRepository: IdRepository
	protected lateinit var statisticsRepository: StatisticsRepository

	// Project state
	protected lateinit var projectDef: ProjectDef
	protected lateinit var projectPath: HPath

	private var nextId = 100 // Start high to avoid conflicts with test data

	@BeforeEach
	override fun setup() {
		super.setup()

		mockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
		coEvery { org.jetbrains.compose.resources.getString(any<org.jetbrains.compose.resources.StringResource>()) } returns "Test String"

		ffs = FakeFileSystem()
		ffs.emulateWindows()

		val rootDir = getDefaultRootDocumentDirectory()
		ffs.createDirectories(rootDir.toPath())

		toml = createTomlSerializer()

		setupMockedDependencies()
		setupKoin()
	}

	@AfterEach
	override fun tearDown() {
		if (::sceneEditorRepository.isInitialized) {
			sceneEditorRepository.onScopeClose(mockk())
		}
		ffs.checkNoOpenFiles()
		unmockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
		super.tearDown()
	}

	private fun setupMockedDependencies() {
		// ID repository - just provides incrementing IDs
		idRepository = mockk()
		coEvery { idRepository.claimNextId() } answers { nextId++ }
		coEvery { idRepository.findNextId() } returns Unit

		// Statistics repository - we don't care about stats in integration tests
		statisticsRepository = mockk(relaxed = true)

		// Sync data repository - controls whether sync is active
		// Default to NOT synchronized so we don't trigger sync marking
		syncDataRepository = mockk()
		every { syncDataRepository.isServerSynchronized() } returns false
	}

	/**
	 * Configure the test with a specific project.
	 * Call this in your test after setting up any specific mock behaviors.
	 */
	protected fun configureProject(projectName: String) {
		projectPath = getProjectsDirectory().div(projectName).toHPath()

		projectDef = ProjectDef(
			name = projectName,
			path = projectPath
		)

		// Create the project from test resources
		createProject(ffs, projectName)

		// Create real datasources
		sceneDatasource = SceneDatasource(projectDef, ffs)
		sceneMetadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		projectMetadataDatasource = ProjectMetadataDatasource(ffs, toml)

		// Create real repository with real datasources
		sceneEditorRepository = SceneEditorRepository(
			projectDef = projectDef,
			syncDataRepository = syncDataRepository,
			idRepository = idRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			sceneMetadataDatasource = sceneMetadataDatasource,
			sceneDatasource = sceneDatasource,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = mockk(relaxed = true),
		)
	}

	/**
	 * Enable server sync mode for tests that need to test sync behavior.
	 * This will cause markForSynchronization to actually run.
	 */
	protected fun enableServerSync() {
		every { syncDataRepository.isServerSynchronized() } returns true
		coEvery { syncDataRepository.isEntityDirty(any()) } returns false
		coEvery { syncDataRepository.markEntityAsDirty(any(), any()) } returns Unit
	}

	/**
	 * Helper to get the archived directory path for assertions.
	 */
	protected fun getArchivedDirectory(): HPath {
		return sceneDatasource.getSceneDirectory().toOkioPath()
			.div(SceneDatasource.ARCHIVED_DIRECTORY)
			.toHPath()
	}
}
