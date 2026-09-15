package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryDeleteAllLocalDataTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projectsRepository: ProjectsRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val toml = createTomlSerializer()

		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		setupKoin()

		projectsRepository = ProjectsRepository(
			fileSystem = ffs,
			globalSettingsStore = globalSettingsStore,
			projectsMetadataDatasource = ProjectMetadataDatasource(ffs, toml),
			toml = toml,
			json = createJsonSerializer(),
			deviceLocaleResolver = mockk<DeviceLocaleResolver>(relaxed = true),
		)
	}

	@Test
	fun `everything under the projects directory is removed but the directory remains`() {
		projectsRepository.createProject("Alpha", seedDefaultLanguage = false)
		projectsRepository.createProject("Beta", seedDefaultLanguage = false)
		ffs.createDirectories("/projects/.ideas/idea-1".toPath())
		ffs.write("/projects/.sync.json".toPath()) { writeUtf8("{}") }

		projectsRepository.deleteAllLocalData()

		assertTrue(ffs.exists("/projects".toPath()))
		assertEquals(emptyList(), ffs.list("/projects".toPath()))
		assertEquals(emptyList(), projectsRepository.getProjects())
	}
}
