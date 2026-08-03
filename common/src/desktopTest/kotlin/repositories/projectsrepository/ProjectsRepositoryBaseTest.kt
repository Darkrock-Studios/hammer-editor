package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import createRootDirectory
import getProjectsDirectory
import io.mockk.coEvery
import io.mockk.coJustAwait
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.SharedFlow
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import utils.BaseTest

abstract class ProjectsRepositoryBaseTest : BaseTest() {
	protected lateinit var ffs: FakeFileSystem
	protected lateinit var settingsRepo: GlobalSettingsStore
	protected lateinit var projectsMetaDatasource: ProjectMetadataDatasource
	protected lateinit var settings: GlobalSettings
	protected lateinit var toml: Toml
	protected lateinit var deviceLocaleResolver: DeviceLocaleResolver

	protected fun projectsRepository(fs: FileSystem = ffs) =
		ProjectsRepository(fs, settingsRepo, projectsMetaDatasource, toml, deviceLocaleResolver)

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		deviceLocaleResolver = mockk()
		every { deviceLocaleResolver.getCurrentLocale() } returns Locale.forLanguage("en", "US")

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