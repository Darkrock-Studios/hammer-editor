package repositories.projectplugins

import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import utils.BaseTest
import kotlin.test.assertEquals

class ProjectPluginsRepositoryTest : BaseTest() {

	private val alpha = testProjectId("Alpha")
	private val beta = testProjectId("Beta")

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin()
	}

	private fun repository() = ProjectPluginsRepository(ProjectPluginsDatasource(fileSystem, toml))

	@Test
	fun `every plugin is on for a project with nothing stored`() = runTest(mainTestDispatcher) {
		assertEquals(emptySet(), repository().disabledPlugins(alpha).first())
	}

	@Test
	fun `turning a plugin off affects only its project, and survives a restart`() = runTest(mainTestDispatcher) {
		val repository = repository()
		repository.setPluginEnabled(alpha, "style", false)

		assertEquals(setOf("style"), repository.disabledPlugins(alpha).first())
		assertEquals(emptySet(), repository.disabledPlugins(beta).first())
		assertEquals(setOf("style"), repository().disabledPlugins(alpha).first())
	}

	@Test
	fun `turning a plugin back on removes it`() = runTest(mainTestDispatcher) {
		val repository = repository()
		repository.setPluginEnabled(alpha, "style", false)
		repository.setPluginEnabled(alpha, "plaintext", false)
		repository.setPluginEnabled(alpha, "style", true)

		assertEquals(setOf("plaintext"), repository().disabledPlugins(alpha).first())
	}

	@Test
	fun `a fresh read sees a change another process made`() = runTest(mainTestDispatcher) {
		val reader = repository()
		assertEquals(emptySet(), reader.readDisabledPlugins(alpha))

		repository().setPluginEnabled(alpha, "style", false)

		assertEquals(setOf("style"), reader.readDisabledPlugins(alpha))
	}

	@Test
	fun `an unreadable file leaves every plugin on`() = runTest(mainTestDispatcher) {
		fileSystem.createDirectories(ProjectPluginsDatasource.FILE_PATH.parent!!)
		fileSystem.write(ProjectPluginsDatasource.FILE_PATH) { writeUtf8("not = [valid") }

		assertEquals(emptySet(), repository().disabledPlugins(alpha).first())
	}
}
