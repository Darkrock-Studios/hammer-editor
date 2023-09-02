package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertTrue

class ProjectsRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml

	lateinit var globalSettingsRepository: GlobalSettingsRepository
	lateinit var settingsFlow: SharedFlow<GlobalSettings>

	private lateinit var metadataRepository: ProjectMetadataRepository
	lateinit var settings: GlobalSettings

	@Before
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		globalSettingsRepository = mockk()

		metadataRepository = mockk(relaxed = true)
		every { metadataRepository.loadMetadata(any()) } returns
			ProjectMetadata(
				info = Info(
					created = Instant.DISTANT_FUTURE,
					lastAccessed = Instant.DISTANT_FUTURE,
				)
			)

		settings = GlobalSettings(
			projectsDirectory = (getDefaultRootDocumentDirectory().toPath() / "Projects").toHPath().path
		)
		every { globalSettingsRepository.globalSettings } returns settings

		settingsFlow = mockk()
		coEvery { settingsFlow.collect(any()) } just Awaits
		every { globalSettingsRepository.globalSettingsUpdates } returns settingsFlow

		val testModule = module {
			single { globalSettingsRepository } bind GlobalSettingsRepository::class
		}
		setupKoin(testModule)

		ffs.createDirectories(settings.projectsDirectory.toPath())
	}

	@Test
	fun `Initialize ProjectsRepository`() = runTest {
		val repo = ProjectsRepositoryOkio(
			fileSystem = ffs,
			globalSettingsRepository = globalSettingsRepository,
			projectsMetadataRepository = metadataRepository,
		)

		advanceUntilIdle()

		verify(exactly = 1) { globalSettingsRepository.globalSettingsUpdates }
		coVerify(exactly = 1) { settingsFlow.collect(any()) }
	}

	@Test
	fun `Create New Project`() = runTest {
		val repo = ProjectsRepositoryOkio(
			fileSystem = ffs,
			globalSettingsRepository = globalSettingsRepository,
			projectsMetadataRepository = metadataRepository,
		)

		val projectName = "Test Project"

		val result = repo.createProject(projectName)
		assertTrue { result.isSuccess }

		val projectPath = settings.projectsDirectory.toPath() / projectName
		ffs.exists(projectPath)

		val newDef = ProjectDef(projectName, projectPath.toHPath())
		val metadataPath = metadataRepository.getMetadataPath(newDef)
		ffs.exists(metadataPath.toOkioPath())
	}
}