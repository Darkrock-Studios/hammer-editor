package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createRootDirectory
import getProjectsDirectory
import io.mockk.coEvery
import io.mockk.coJustAwait
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.SharedFlow
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import utils.BaseTest

abstract class ProjectsRepositoryBaseTest : BaseTest() {
	protected lateinit var ffs: FakeFileSystem
	protected lateinit var settingsRepo: GlobalSettingsRepository
	protected lateinit var projectsMetaDatasource: ProjectMetadataDatasource
	protected lateinit var settings: GlobalSettings
	protected lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		projectsMetaDatasource = ProjectMetadataDatasource(ffs, toml)

		settingsRepo = mockk()
		settings = mockk()
		every { settingsRepo.globalSettings } answers { settings }
		coEvery { settingsRepo.globalSettingsUpdates } coAnswers {
			val flow = mockk<SharedFlow<GlobalSettings>>()
			coJustAwait { flow.collect(any()) }
			flow
		}
		every { settings.projectsDirectory } answers { getProjectsDirectory().toString() }

		createRootDirectory(ffs)
		setupKoin()
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}
}